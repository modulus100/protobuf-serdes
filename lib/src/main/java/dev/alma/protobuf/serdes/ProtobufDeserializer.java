package dev.alma.protobuf.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.util.Objects;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public final class ProtobufDeserializer<T extends MessageLite> implements Deserializer<T> {

    private final Parser<T> parser;

    public ProtobufDeserializer(Parser<T> parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Failed to deserialize protobuf payload", e);
        }
    }
}
