package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import proto.test.v1.UserCreated;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class ProtobufDeserializerTest {

    @Test
    void deserializesValidBytes() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer();
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-2")
            .setEmail("u-2@example.com")
            .setCreatedAtEpochMs(1_739_801_235_000L)
            .build();

        UserCreated deserialized = deserializer.deserialize("users", value.toByteArray());

        assertEquals(value, deserialized);
    }

    @Test
    void returnsNullWhenBytesAreNull() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer();
        assertNull(deserializer.deserialize("users", null));
    }

    @Test
    void throwsSerializationExceptionForInvalidBytes() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer();
        assertThrows(
            SerializationException.class,
            () -> deserializer.deserialize("users", new byte[] {1, 2, 3, 4, 5})
        );
    }

    @Test
    void deserializesWithConfiguredClassName() {
        ProtobufDeserializer<UserCreated> configuredDeserializer = configuredDeserializer();

        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-3")
            .setEmail("u-3@example.com")
            .setCreatedAtEpochMs(1_739_801_236_000L)
            .build();

        UserCreated deserialized = configuredDeserializer.deserialize("users", value.toByteArray());

        assertEquals(value, deserialized);
    }

    @Test
    void throwsConfigExceptionWhenConfiguredClassIsMissing() {
        ProtobufDeserializer<UserCreated> configuredDeserializer = new ProtobufDeserializer<>();
        assertThrows(ConfigException.class, () -> configuredDeserializer.configure(Map.of(), false));
    }

    @Test
    void throwsConfigExceptionWhenConfiguredClassNameIsInvalid() {
        ProtobufDeserializer<UserCreated> configuredDeserializer = new ProtobufDeserializer<>();
        assertThrows(
            ConfigException.class,
            () -> configuredDeserializer.configure(
                Map.of(ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, "com.missing.DoesNotExist"),
                false
            )
        );
    }

    @Test
    void throwsSerializationExceptionWhenNotConfigured() {
        ProtobufDeserializer<UserCreated> configuredDeserializer = new ProtobufDeserializer<>();
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-4")
            .setEmail("u-4@example.com")
            .setCreatedAtEpochMs(1_739_801_237_000L)
            .build();

        assertThrows(
            SerializationException.class,
            () -> configuredDeserializer.deserialize("users", value.toByteArray())
        );
    }

    @Test
    void deserializesConfluentPayloadWhenConfiguredConfluent() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer("confluent");
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-5")
            .setEmail("u-5@example.com")
            .setCreatedAtEpochMs(1_739_801_238_000L)
            .build();

        byte[] payload = confluentWirePayload(value.toByteArray(), 14, 0);
        UserCreated deserialized = deserializer.deserialize("users", payload);

        assertEquals(value, deserialized);
    }

    @Test
    void deserializesConfluentPayloadWithMultipleMessageIndexes() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer("confluent");
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-6")
            .setEmail("u-6@example.com")
            .setCreatedAtEpochMs(1_739_801_239_000L)
            .build();

        byte[] payload = confluentWirePayload(value.toByteArray(), 99, 1, 0, 3);
        UserCreated deserialized = deserializer.deserialize("users", payload);

        assertEquals(value, deserialized);
    }

    @Test
    void deserializesRawPayloadWhenConfiguredAuto() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer("auto");
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-7")
            .setEmail("u-7@example.com")
            .setCreatedAtEpochMs(1_739_801_240_000L)
            .build();

        UserCreated deserialized = deserializer.deserialize("users", value.toByteArray());

        assertEquals(value, deserialized);
    }

    @Test
    void deserializesConfluentPayloadWhenConfiguredAuto() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer("auto");
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-8")
            .setEmail("u-8@example.com")
            .setCreatedAtEpochMs(1_739_801_241_000L)
            .build();

        byte[] payload = confluentWirePayload(value.toByteArray(), 7, 0);
        UserCreated deserialized = deserializer.deserialize("users", payload);

        assertEquals(value, deserialized);
    }

    @Test
    void defaultsToAutoAndDeserializesRawPayload() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializerWithoutPayloadFormat();
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-default-auto-raw-1")
            .setEmail("u-default-auto-raw-1@example.com")
            .setCreatedAtEpochMs(1_739_801_243_000L)
            .build();

        UserCreated deserialized = deserializer.deserialize("users", value.toByteArray());

        assertEquals(value, deserialized);
    }

    @Test
    void defaultsToAutoAndDeserializesConfluentPayload() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializerWithoutPayloadFormat();
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-default-auto-confluent-1")
            .setEmail("u-default-auto-confluent-1@example.com")
            .setCreatedAtEpochMs(1_739_801_244_000L)
            .build();

        byte[] payload = confluentWirePayload(value.toByteArray(), 11, 0);
        UserCreated deserialized = deserializer.deserialize("users", payload);

        assertEquals(value, deserialized);
    }

    @Test
    void throwsSerializationExceptionWhenConfluentConfiguredAndPayloadIsRaw() {
        ProtobufDeserializer<UserCreated> deserializer = configuredDeserializer("confluent");
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-9")
            .setEmail("u-9@example.com")
            .setCreatedAtEpochMs(1_739_801_242_000L)
            .build();

        assertThrows(
            SerializationException.class,
            () -> deserializer.deserialize("users", value.toByteArray())
        );
    }

    @Test
    void throwsConfigExceptionWhenPayloadFormatIsInvalid() {
        ProtobufDeserializer<UserCreated> deserializer = new ProtobufDeserializer<>();
        assertThrows(
            ConfigException.class,
            () -> deserializer.configure(
                Map.of(
                    ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, UserCreated.class.getName(),
                    ProtobufDeserializer.PAYLOAD_FORMAT_CONFIG, "unsupported"
                ),
                false
            )
        );
    }

    private ProtobufDeserializer<UserCreated> configuredDeserializer() {
        return configuredDeserializer("raw");
    }

    private ProtobufDeserializer<UserCreated> configuredDeserializer(String payloadFormat) {
        ProtobufDeserializer<UserCreated> deserializer = new ProtobufDeserializer<>();
        deserializer.configure(
            Map.of(
                ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, UserCreated.class.getName(),
                ProtobufDeserializer.PAYLOAD_FORMAT_CONFIG, payloadFormat
            ),
            false
        );
        return deserializer;
    }

    private ProtobufDeserializer<UserCreated> configuredDeserializerWithoutPayloadFormat() {
        ProtobufDeserializer<UserCreated> deserializer = new ProtobufDeserializer<>();
        deserializer.configure(
            Map.of(ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, UserCreated.class.getName()),
            false
        );
        return deserializer;
    }

    private byte[] confluentWirePayload(byte[] protobufPayload, int schemaId, int... messageIndexes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write((schemaId >>> 24) & 0xFF);
        out.write((schemaId >>> 16) & 0xFF);
        out.write((schemaId >>> 8) & 0xFF);
        out.write(schemaId & 0xFF);

        if (messageIndexes.length == 1 && messageIndexes[0] == 0) {
            writeUnsignedVarInt(out, 0);
        } else {
            writeUnsignedVarInt(out, encodeZigZag32(messageIndexes.length));
            for (int messageIndex : messageIndexes) {
                writeUnsignedVarInt(out, encodeZigZag32(messageIndex));
            }
        }

        out.writeBytes(protobufPayload);
        return out.toByteArray();
    }

    private int encodeZigZag32(int value) {
        return (value << 1) ^ (value >> 31);
    }

    private void writeUnsignedVarInt(ByteArrayOutputStream out, int value) {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            out.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        out.write(remaining);
    }
}
