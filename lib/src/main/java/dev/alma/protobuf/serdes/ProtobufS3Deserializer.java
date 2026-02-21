package dev.alma.protobuf.serdes;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public final class ProtobufS3Deserializer<T extends Message> implements Deserializer<T> {

    public static final String KEY_CLASS_NAME_CONFIG = "protobuf.s3.key.class";
    public static final String VALUE_CLASS_NAME_CONFIG = "protobuf.s3.value.class";
    public static final String SUBJECT_HEADER_CONFIG = "protobuf.s3.subject.header";
    public static final String VERSION_HEADER_CONFIG = "protobuf.s3.version.header";
    public static final String MESSAGE_TYPE_HEADER_CONFIG = "protobuf.s3.message.type.header";
    public static final String S3_BUCKET_CONFIG = "protobuf.s3.bucket";
    public static final String S3_REGION_CONFIG = "protobuf.s3.region";
    public static final String S3_ENDPOINT_CONFIG = "protobuf.s3.endpoint";
    public static final String S3_PATH_STYLE_ACCESS_CONFIG = "protobuf.s3.path.style.access";
    public static final String S3_ACCESS_KEY_CONFIG = "protobuf.s3.access.key";
    public static final String S3_SECRET_KEY_CONFIG = "protobuf.s3.secret.key";
    public static final String S3_SESSION_TOKEN_CONFIG = "protobuf.s3.session.token";
    public static final String S3_KEY_TEMPLATE_CONFIG = "protobuf.s3.key.template";
    public static final String CACHE_MAX_SIZE_CONFIG = "protobuf.s3.cache.max.size";
    public static final String CACHE_TTL_SECONDS_CONFIG = "protobuf.s3.cache.ttl.seconds";

    private static final String DEFAULT_SUBJECT_HEADER = "protobuf.subject";
    private static final String DEFAULT_VERSION_HEADER = "protobuf.schema.version";
    private static final String DEFAULT_MESSAGE_TYPE_HEADER = "protobuf.message.type";
    private static final String DEFAULT_S3_KEY_TEMPLATE = "{subject}/{version}/descriptor.pb";
    private static final long DEFAULT_CACHE_MAX_SIZE = 1000L;
    private static final long DEFAULT_CACHE_TTL_SECONDS = 3600L;

    private volatile Parser<T> specificParser;
    private volatile Cache<String, DescriptorSchema> schemaCache;
    private volatile DescriptorBytesLoader descriptorBytesLoader;
    private String subjectHeaderName = DEFAULT_SUBJECT_HEADER;
    private String versionHeaderName = DEFAULT_VERSION_HEADER;
    private String messageTypeHeaderName = DEFAULT_MESSAGE_TYPE_HEADER;

    public ProtobufS3Deserializer() {
    }

    ProtobufS3Deserializer(DescriptorBytesLoader descriptorBytesLoader) {
        this.descriptorBytesLoader = descriptorBytesLoader;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        Object subjectHeader = configs.get(SUBJECT_HEADER_CONFIG);
        Object versionHeader = configs.get(VERSION_HEADER_CONFIG);
        Object messageTypeHeader = configs.get(MESSAGE_TYPE_HEADER_CONFIG);
        subjectHeaderName = subjectHeader == null ? DEFAULT_SUBJECT_HEADER : subjectHeader.toString();
        versionHeaderName = versionHeader == null ? DEFAULT_VERSION_HEADER : versionHeader.toString();
        messageTypeHeaderName = messageTypeHeader == null ? DEFAULT_MESSAGE_TYPE_HEADER : messageTypeHeader.toString();

        long cacheMaxSize = readLong(configs, CACHE_MAX_SIZE_CONFIG, DEFAULT_CACHE_MAX_SIZE);
        long cacheTtlSeconds = readLong(configs, CACHE_TTL_SECONDS_CONFIG, DEFAULT_CACHE_TTL_SECONDS);
        schemaCache = CacheBuilder.newBuilder()
            .maximumSize(cacheMaxSize)
            .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
            .build();

        String classConfigKey = isKey ? KEY_CLASS_NAME_CONFIG : VALUE_CLASS_NAME_CONFIG;
        Object classConfiguredType = configs.get(classConfigKey);
        specificParser = classConfiguredType == null ? null : parserFromConfig(classConfiguredType, classConfigKey);

        if (descriptorBytesLoader == null) {
            descriptorBytesLoader = createS3DescriptorBytesLoader(configs);
        }
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        return deserialize(topic, null, data);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        if (headers == null) {
            throw new SerializationException("Headers are required for ProtobufS3Deserializer");
        }
        if (schemaCache == null || descriptorBytesLoader == null) {
            throw new SerializationException("Deserializer is not configured");
        }

        String subject = readRequiredHeader(headers, subjectHeaderName);
        String versionValue = readRequiredHeader(headers, versionHeaderName);
        String messageType = readRequiredHeader(headers, messageTypeHeaderName);

        int version;
        try {
            version = Integer.parseInt(versionValue);
        } catch (NumberFormatException e) {
            throw new SerializationException("Invalid protobuf schema version header value: " + versionValue, e);
        }

        DescriptorSchema schema;
        try {
            schema = schemaCache.get(schemaCacheKey(subject, version), () -> loadSchema(subject, version));
        } catch (ExecutionException e) {
            throw new SerializationException(
                "Failed to load schema for subject=" + subject + ", version=" + version,
                e.getCause()
            );
        }

        Descriptor descriptor = schema.messageDescriptor(messageType);
        if (descriptor == null) {
            throw new SerializationException(
                "Message type " + messageType + " was not found in schema for subject=" + subject + ", version=" + version
            );
        }

        try {
            if (specificParser != null) {
                return specificParser.parseFrom(data);
            }
            return (T) DynamicMessage.parseFrom(descriptor, data);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Failed to deserialize protobuf payload", e);
        }
    }

    @Override
    public void close() {
        if (descriptorBytesLoader != null) {
            descriptorBytesLoader.close();
        }
    }

    private String schemaCacheKey(String subject, int version) {
        return subject + "|" + version;
    }

    private DescriptorSchema loadSchema(String subject, int version) {
        byte[] descriptorBytes = descriptorBytesLoader.load(subject, version);
        FileDescriptorSet descriptorSet;
        try {
            descriptorSet = FileDescriptorSet.parseFrom(descriptorBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new SerializationException("Invalid descriptor set for subject=" + subject + ", version=" + version, e);
        }

        Map<String, FileDescriptorProto> fileProtoByName = new HashMap<>();
        for (FileDescriptorProto file : descriptorSet.getFileList()) {
            fileProtoByName.put(file.getName(), file);
        }

        Map<String, FileDescriptor> fileDescriptorByName = new HashMap<>();
        for (String fileName : fileProtoByName.keySet()) {
            buildFileDescriptor(fileName, fileProtoByName, fileDescriptorByName);
        }

        Map<String, Descriptor> descriptorByFullName = new HashMap<>();
        for (FileDescriptor fileDescriptor : fileDescriptorByName.values()) {
            for (Descriptor descriptor : fileDescriptor.getMessageTypes()) {
                indexMessageDescriptors(descriptor, descriptorByFullName);
            }
        }

        return new DescriptorSchema(descriptorByFullName);
    }

    private FileDescriptor buildFileDescriptor(
        String fileName,
        Map<String, FileDescriptorProto> fileProtoByName,
        Map<String, FileDescriptor> fileDescriptorByName
    ) {
        FileDescriptor existing = fileDescriptorByName.get(fileName);
        if (existing != null) {
            return existing;
        }

        FileDescriptorProto fileProto = fileProtoByName.get(fileName);
        if (fileProto == null) {
            throw new SerializationException("Descriptor dependency not found: " + fileName);
        }

        FileDescriptor[] dependencies = new FileDescriptor[fileProto.getDependencyCount()];
        for (int i = 0; i < fileProto.getDependencyCount(); i++) {
            dependencies[i] = buildFileDescriptor(fileProto.getDependency(i), fileProtoByName, fileDescriptorByName);
        }

        try {
            FileDescriptor built = FileDescriptor.buildFrom(fileProto, dependencies);
            fileDescriptorByName.put(fileName, built);
            return built;
        } catch (DescriptorValidationException e) {
            throw new SerializationException("Invalid descriptor file: " + fileName, e);
        }
    }

    private void indexMessageDescriptors(Descriptor descriptor, Map<String, Descriptor> descriptorsByFullName) {
        descriptorsByFullName.put(descriptor.getFullName(), descriptor);
        for (Descriptor nested : descriptor.getNestedTypes()) {
            indexMessageDescriptors(nested, descriptorsByFullName);
        }
    }

    private String readRequiredHeader(Headers headers, String headerName) {
        if (headers.lastHeader(headerName) == null) {
            throw new SerializationException("Missing required kafka header: " + headerName);
        }
        String value = new String(headers.lastHeader(headerName).value(), StandardCharsets.UTF_8);
        if (value.isBlank()) {
            throw new SerializationException("Kafka header is blank: " + headerName);
        }
        return value;
    }

    private long readLong(Map<String, ?> configs, String key, long defaultValue) {
        Object value = configs.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new ConfigException(key, value, "Expected numeric value");
        }
    }

    private DescriptorBytesLoader createS3DescriptorBytesLoader(Map<String, ?> configs) {
        Object bucketValue = configs.get(S3_BUCKET_CONFIG);
        if (bucketValue == null || bucketValue.toString().isBlank()) {
            throw new ConfigException(S3_BUCKET_CONFIG, bucketValue, "S3 bucket is required");
        }
        String bucket = bucketValue.toString();

        Object keyTemplateValue = configs.get(S3_KEY_TEMPLATE_CONFIG);
        String keyTemplate = keyTemplateValue == null ? DEFAULT_S3_KEY_TEMPLATE : keyTemplateValue.toString();

        S3ClientBuilder builder = S3Client.builder();
        Object regionValue = configs.get(S3_REGION_CONFIG);
        if (regionValue != null && !regionValue.toString().isBlank()) {
            builder.region(Region.of(regionValue.toString()));
        }

        Object endpointValue = configs.get(S3_ENDPOINT_CONFIG);
        if (endpointValue != null && !endpointValue.toString().isBlank()) {
            builder.endpointOverride(URI.create(endpointValue.toString()));
        }

        Object pathStyleValue = configs.get(S3_PATH_STYLE_ACCESS_CONFIG);
        if (pathStyleValue != null && Boolean.parseBoolean(pathStyleValue.toString())) {
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }

        Object accessKeyValue = configs.get(S3_ACCESS_KEY_CONFIG);
        Object secretKeyValue = configs.get(S3_SECRET_KEY_CONFIG);
        Object sessionTokenValue = configs.get(S3_SESSION_TOKEN_CONFIG);
        if (accessKeyValue != null || secretKeyValue != null || sessionTokenValue != null) {
            if (accessKeyValue == null || secretKeyValue == null) {
                throw new ConfigException(
                    S3_SECRET_KEY_CONFIG,
                    secretKeyValue,
                    "Both " + S3_ACCESS_KEY_CONFIG + " and " + S3_SECRET_KEY_CONFIG + " are required when setting explicit credentials"
                );
            }
            String accessKey = accessKeyValue.toString();
            String secretKey = secretKeyValue.toString();
            if (sessionTokenValue == null || sessionTokenValue.toString().isBlank()) {
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                );
            } else {
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(accessKey, secretKey, sessionTokenValue.toString())
                    )
                );
            }
        }

        return new S3DescriptorBytesLoader(builder.build(), bucket, keyTemplate);
    }

    @SuppressWarnings("unchecked")
    private Parser<T> parserFromConfig(Object configuredType, String configKey) {
        Class<?> messageClass = resolveMessageClass(configuredType, configKey);
        if (!Message.class.isAssignableFrom(messageClass)) {
            throw new ConfigException(configKey, configuredType, "Configured type must implement Message");
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
