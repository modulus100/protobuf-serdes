package dev.alma.protobuf.serdes;

import com.google.protobuf.MessageLite;
import org.apache.kafka.common.serialization.Serializer;

public final class ProtobufSerializer<T extends MessageLite> implements Serializer<T> {

    @Override
    public byte[] serialize(String topic, T data) {
        return data == null ? null : data.toByteArray();
    }
}
