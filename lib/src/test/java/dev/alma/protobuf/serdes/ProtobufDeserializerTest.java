package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import proto.test.v1.UserCreated;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

class ProtobufDeserializerTest {

    private final ProtobufDeserializer<UserCreated> deserializer =
        new ProtobufDeserializer<>(UserCreated.parser());

    @Test
    void deserializesValidBytes() {
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
        assertNull(deserializer.deserialize("users", null));
    }

    @Test
    void throwsSerializationExceptionForInvalidBytes() {
        assertThrows(
            SerializationException.class,
            () -> deserializer.deserialize("users", new byte[] {1, 2, 3, 4, 5})
        );
    }
}
