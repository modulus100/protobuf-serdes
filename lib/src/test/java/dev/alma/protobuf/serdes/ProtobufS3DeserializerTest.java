package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Message;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import proto.test.v1.UserCreated;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class ProtobufS3DeserializerTest {

    @Test
    void deserializesDynamicMessageUsingDescriptorFromLoader() throws Exception {
        CountingDescriptorLoader loader = new CountingDescriptorLoader(descriptorSetBytes());
        ProtobufS3Deserializer<Message> deserializer = new ProtobufS3Deserializer<>(loader);
        deserializer.configure(Map.of(), false);

        UserCreated payload = sampleMessage();
        Message deserialized = deserializer.deserialize("users", defaultHeaders(), payload.toByteArray());

        assertInstanceOf(com.google.protobuf.DynamicMessage.class, deserialized);
        assertEquals(payload.toByteString(), deserialized.toByteString());
        assertEquals(1, loader.loadCalls());
    }

    @Test
    void deserializesTypedMessageWhenValueClassConfigured() {
        CountingDescriptorLoader loader = new CountingDescriptorLoader(descriptorSetBytes());
        ProtobufS3Deserializer<UserCreated> deserializer = new ProtobufS3Deserializer<>(loader);
        deserializer.configure(Map.of(ProtobufS3Deserializer.VALUE_CLASS_NAME_CONFIG, UserCreated.class.getName()), false);

        UserCreated payload = sampleMessage();
        UserCreated deserialized = deserializer.deserialize("users", defaultHeaders(), payload.toByteArray());

        assertEquals(payload, deserialized);
        assertEquals(1, loader.loadCalls());
    }

    @Test
    void reusesCachedSchemaForSameSubjectAndVersion() {
        CountingDescriptorLoader loader = new CountingDescriptorLoader(descriptorSetBytes());
        ProtobufS3Deserializer<Message> deserializer = new ProtobufS3Deserializer<>(loader);
        deserializer.configure(Map.of(), false);
        RecordHeaders headers = defaultHeaders();
        UserCreated payload = sampleMessage();

        deserializer.deserialize("users", headers, payload.toByteArray());
        deserializer.deserialize("users", headers, payload.toByteArray());

        assertEquals(1, loader.loadCalls());
    }

    @Test
    void throwsWhenVersionHeaderIsInvalid() {
        CountingDescriptorLoader loader = new CountingDescriptorLoader(descriptorSetBytes());
        ProtobufS3Deserializer<Message> deserializer = new ProtobufS3Deserializer<>(loader);
        deserializer.configure(Map.of(), false);
        RecordHeaders headers = new RecordHeaders();
        headers.add("protobuf.subject", "users".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.schema.version", "v1".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.message.type", UserCreated.getDescriptor().getFullName().getBytes(StandardCharsets.UTF_8));

        assertThrows(
            SerializationException.class,
            () -> deserializer.deserialize("users", headers, sampleMessage().toByteArray())
        );
    }

    @Test
    void throwsWhenRequiredHeaderIsMissing() {
        CountingDescriptorLoader loader = new CountingDescriptorLoader(descriptorSetBytes());
        ProtobufS3Deserializer<Message> deserializer = new ProtobufS3Deserializer<>(loader);
        deserializer.configure(Map.of(), false);
        RecordHeaders headers = new RecordHeaders();
        headers.add("protobuf.subject", "users".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.schema.version", "1".getBytes(StandardCharsets.UTF_8));

        assertThrows(
            SerializationException.class,
            () -> deserializer.deserialize("users", headers, sampleMessage().toByteArray())
        );
    }

    @Test
    void throwsWhenMessageTypeNotFoundInDescriptorSet() {
        CountingDescriptorLoader loader = new CountingDescriptorLoader(descriptorSetBytes());
        ProtobufS3Deserializer<Message> deserializer = new ProtobufS3Deserializer<>(loader);
        deserializer.configure(Map.of(), false);
        RecordHeaders headers = new RecordHeaders();
        headers.add("protobuf.subject", "users".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.schema.version", "1".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.message.type", "proto.test.v1.DoesNotExist".getBytes(StandardCharsets.UTF_8));

        assertThrows(
            SerializationException.class,
            () -> deserializer.deserialize("users", headers, sampleMessage().toByteArray())
        );
    }

    private byte[] descriptorSetBytes() {
        return FileDescriptorSet.newBuilder()
            .addFile(UserCreated.getDescriptor().getFile().toProto())
            .build()
            .toByteArray();
    }

    private RecordHeaders defaultHeaders() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("protobuf.subject", "users".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.schema.version", "1".getBytes(StandardCharsets.UTF_8));
        headers.add("protobuf.message.type", UserCreated.getDescriptor().getFullName().getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    private UserCreated sampleMessage() {
        return UserCreated.newBuilder()
            .setUserId("u-s3-des-1")
            .setEmail("u-s3-des-1@example.com")
            .setCreatedAtEpochMs(1_739_801_241_000L)
            .build();
    }

    private final class CountingDescriptorLoader implements DescriptorBytesLoader {

        private final byte[] descriptorBytes;
        private final AtomicInteger loads = new AtomicInteger();

        private CountingDescriptorLoader(byte[] descriptorBytes) {
            this.descriptorBytes = descriptorBytes;
        }

        @Override
        public byte[] load(String subject, int version) {
            loads.incrementAndGet();
            return descriptorBytes;
        }

        int loadCalls() {
            return loads.get();
        }
    }
}
