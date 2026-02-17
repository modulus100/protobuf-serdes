package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import proto.test.v1.UserCreated;
import org.junit.jupiter.api.Test;

class ProtobufSerializerTest {

    private final ProtobufSerializer<UserCreated> serializer = new ProtobufSerializer<>();

    @Test
    void serializesMessageToWireFormat() {
        UserCreated value = UserCreated.newBuilder()
            .setUserId("u-1")
            .setEmail("u-1@example.com")
            .setCreatedAtEpochMs(1_739_801_234_000L)
            .build();

        byte[] bytes = serializer.serialize("users", value);

        assertArrayEquals(value.toByteArray(), bytes);
    }

    @Test
    void returnsNullWhenValueIsNull() {
        assertNull(serializer.serialize("users", null));
    }
}
