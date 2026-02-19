package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private ProtobufDeserializer<UserCreated> configuredDeserializer() {
        ProtobufDeserializer<UserCreated> deserializer = new ProtobufDeserializer<>();
        deserializer.configure(
            Map.of(ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, UserCreated.class.getName()),
            false
        );
        return deserializer;
    }
}
