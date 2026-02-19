package dev.alma.protobuf.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

public final class ProtobufDeserializer<T extends MessageLite> implements Deserializer<T> {

    public static final String KEY_CLASS_NAME_CONFIG = "protobuf.key.class";
    public static final String VALUE_CLASS_NAME_CONFIG = "protobuf.value.class";

    private volatile Parser<T> parser;

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
        try {
            return parser.parseFrom(data);
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
}
