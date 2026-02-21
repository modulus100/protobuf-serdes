package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import proto.test.v1.UserCreated;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class ProtobufS3SerializerTest {

    @Test
    void serializesPayloadAndAddsHeadersFromConfig() {
        ProtobufS3Serializer<UserCreated> serializer = new ProtobufS3Serializer<>();
        serializer.configure(
            Map.of(
                ProtobufS3Serializer.SUBJECT_CONFIG, "user-created",
                ProtobufS3Serializer.VERSION_CONFIG, "1"
            ),
            false
        );

        UserCreated payload = sampleMessage();
        RecordHeaders headers = new RecordHeaders();

        byte[] serialized = serializer.serialize("users", headers, payload);

        assertArrayEquals(payload.toByteArray(), serialized);
        assertEquals("user-created", headerValue(headers, "protobuf.subject"));
        assertEquals("1", headerValue(headers, "protobuf.schema.version"));
        assertEquals(payload.getDescriptorForType().getFullName(), headerValue(headers, "protobuf.message.type"));
    }

    @Test
    void usesProvidedHeaderValuesWhenPresent() {
        ProtobufS3Serializer<UserCreated> serializer = new ProtobufS3Serializer<>();
        serializer.configure(
            Map.of(
                ProtobufS3Serializer.SUBJECT_CONFIG, "configured-subject",
                ProtobufS3Serializer.VERSION_CONFIG, "99"
            ),
            false
        );

        UserCreated payload = sampleMessage();
        RecordHeaders headers = new RecordHeaders();
        headers.add("protobuf.subject", "header-subject".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.schema.version", "3".getBytes(StandardCharsets.UTF_8));

        serializer.serialize("users", headers, payload);

        assertEquals("header-subject", headerValue(headers, "protobuf.subject"));
        assertEquals("3", headerValue(headers, "protobuf.schema.version"));
    }

    @Test
    void throwsWhenSubjectOrVersionMissing() {
        ProtobufS3Serializer<UserCreated> serializer = new ProtobufS3Serializer<>();
        serializer.configure(Map.of(), false);

        assertThrows(
            SerializationException.class,
            () -> serializer.serialize("users", new RecordHeaders(), sampleMessage())
        );
    }

    @Test
    void returnsNullWhenPayloadIsNull() {
        ProtobufS3Serializer<UserCreated> serializer = new ProtobufS3Serializer<>();
        serializer.configure(Map.of(), false);
        assertNull(serializer.serialize("users", new RecordHeaders(), null));
    }

    private String headerValue(RecordHeaders headers, String key) {
        return new String(headers.lastHeader(key).value(), StandardCharsets.UTF_8);
    }

    private UserCreated sampleMessage() {
        return UserCreated.newBuilder()
            .setUserId("u-s3-ser-1")
            .setEmail("u-s3-ser-1@example.com")
            .setCreatedAtEpochMs(1_739_801_240_000L)
            .build();
    }
}
