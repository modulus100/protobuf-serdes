package dev.alma.protobuf.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public final class ProtobufDeserializer<T extends MessageLite> implements Deserializer<T> {

    public static final String KEY_CLASS_NAME_CONFIG = "protobuf.key.class";
    public static final String VALUE_CLASS_NAME_CONFIG = "protobuf.value.class";
    public static final String PAYLOAD_FORMAT_CONFIG = "protobuf.payload.format";
    private static final int CONFLUENT_WIRE_PREFIX_BYTES = 1 + 4;
    private static final int MAX_VARINT_BYTES = 5;

    private volatile Parser<T> parser;
    private volatile PayloadFormat payloadFormat = PayloadFormat.AUTO;

    public ProtobufDeserializer() {
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        String configKey = isKey ? KEY_CLASS_NAME_CONFIG : VALUE_CLASS_NAME_CONFIG;
        Object configuredType = configs.get(configKey);
        if (configuredType == null) {
            throw new ConfigException(
                configKey,
                null,
                "Missing protobuf message class. Set a class name like com.my.company.MyMessage."
            );
        }
        parser = parserFromConfig(configuredType, configKey);
        payloadFormat = payloadFormatFromConfig(configs.get(PAYLOAD_FORMAT_CONFIG));
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        if (parser == null) {
            throw new SerializationException(
                "Deserializer is not configured. Set "
                    + VALUE_CLASS_NAME_CONFIG + "/" + KEY_CLASS_NAME_CONFIG + "."
            );
        }

        byte[] payload;
        if (payloadFormat == PayloadFormat.RAW) {
            payload = data;
        } else if (payloadFormat == PayloadFormat.AUTO) {
            payload = isConfluentWirePayload(data) ? unwrapConfluentWirePayload(data) : data;
        } else {
            payload = unwrapConfluentWirePayload(data);
        }

        try {
            return parser.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Failed to deserialize protobuf payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Parser<T> parserFromConfig(Object configuredType, String configKey) {
        Class<?> messageClass = resolveMessageClass(configuredType, configKey);
        if (!MessageLite.class.isAssignableFrom(messageClass)) {
            throw new ConfigException(configKey, configuredType, "Configured type must implement MessageLite");
        }

        try {
            Method parserMethod = messageClass.getMethod("parser");
            Object parser = parserMethod.invoke(null);
            if (!(parser instanceof Parser<?> typedParser)) {
                throw new ConfigException(configKey, configuredType, "Failed to obtain parser from configured type");
            }
            return (Parser<T>) typedParser;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new ConfigException(
                configKey,
                configuredType,
                "Configured type must have a public static parser() method"
            );
        }
    }

    private Class<?> resolveMessageClass(Object configuredType, String configKey) {
        if (configuredType instanceof Class<?> clazz) {
            return clazz;
        }
        if (configuredType instanceof String className) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new ConfigException(configKey, configuredType, "Configured class was not found");
            }
        }
        throw new ConfigException(configKey, configuredType, "Expected Class<?> or class name String");
    }

    private PayloadFormat payloadFormatFromConfig(Object configuredFormat) {
        if (configuredFormat == null) {
            return PayloadFormat.AUTO;
        }

        String normalized = configuredFormat.toString().trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "raw" -> PayloadFormat.RAW;
            case "confluent" -> PayloadFormat.CONFLUENT;
            case "auto" -> PayloadFormat.AUTO;
            default -> throw new ConfigException(
                PAYLOAD_FORMAT_CONFIG,
                configuredFormat,
                "Unsupported payload format. Use one of: raw, confluent, auto"
            );
        };
    }

    private boolean isConfluentWirePayload(byte[] data) {
        return data.length > CONFLUENT_WIRE_PREFIX_BYTES && data[0] == 0;
    }

    private byte[] unwrapConfluentWirePayload(byte[] data) {
        if (data.length <= CONFLUENT_WIRE_PREFIX_BYTES) {
            throw new SerializationException("Invalid Confluent protobuf payload: too short");
        }
        if (data[0] != 0) {
            throw new SerializationException("Invalid Confluent protobuf payload: missing magic byte");
        }

        int offset = CONFLUENT_WIRE_PREFIX_BYTES;
        VarIntReadResult messageIndexCountResult = readVarInt(data, offset);
        int messageIndexCount = decodeZigZag32(messageIndexCountResult.value);
        offset = messageIndexCountResult.nextOffset;

        // Confluent optimized form: first varint value 0 means single [0] index.
        if (messageIndexCount != 0) {
            if (messageIndexCount < 0) {
                throw new SerializationException("Invalid Confluent protobuf payload: negative message-index count");
            }
            for (int i = 0; i < messageIndexCount; i++) {
                VarIntReadResult ignored = readVarInt(data, offset);
                offset = ignored.nextOffset;
            }
        }

        return Arrays.copyOfRange(data, offset, data.length);
    }

    private VarIntReadResult readVarInt(byte[] data, int offset) {
        int value = 0;
        int shift = 0;
        int index = offset;
        while (index < data.length && shift < 32 && index - offset < MAX_VARINT_BYTES) {
            int b = data[index++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new VarIntReadResult(value, index);
            }
            shift += 7;
        }
        throw new SerializationException("Invalid Confluent protobuf payload: malformed varint");
    }

    private int decodeZigZag32(int value) {
        return (value >>> 1) ^ -(value & 1);
    }

    private enum PayloadFormat {
        RAW,
        CONFLUENT,
        AUTO
    }

    private record VarIntReadResult(int value, int nextOffset) {
    }
}
