package dev.alma.protobuf.serdes;

import com.google.protobuf.Message;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.Serializer;

public final class ProtobufS3Serializer<T extends Message> implements Serializer<T> {

    public static final String SUBJECT_CONFIG = "protobuf.s3.subject";
    public static final String VERSION_CONFIG = "protobuf.s3.version";
    public static final String SUBJECT_HEADER_CONFIG = "protobuf.s3.subject.header";
    public static final String VERSION_HEADER_CONFIG = "protobuf.s3.version.header";
    public static final String MESSAGE_TYPE_HEADER_CONFIG = "protobuf.s3.message.type.header";

    private static final String DEFAULT_SUBJECT_HEADER = "protobuf.subject";
    private static final String DEFAULT_VERSION_HEADER = "protobuf.schema.version";
    private static final String DEFAULT_MESSAGE_TYPE_HEADER = "protobuf.message.type";

    private String configuredSubject;
    private String configuredVersion;
    private String subjectHeaderName = DEFAULT_SUBJECT_HEADER;
    private String versionHeaderName = DEFAULT_VERSION_HEADER;
    private String messageTypeHeaderName = DEFAULT_MESSAGE_TYPE_HEADER;

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        Object subject = configs.get(SUBJECT_CONFIG);
        Object version = configs.get(VERSION_CONFIG);
        Object subjectHeader = configs.get(SUBJECT_HEADER_CONFIG);
        Object versionHeader = configs.get(VERSION_HEADER_CONFIG);
        Object messageTypeHeader = configs.get(MESSAGE_TYPE_HEADER_CONFIG);

        configuredSubject = subject == null ? null : subject.toString();
        configuredVersion = version == null ? null : version.toString();
        subjectHeaderName = subjectHeader == null ? DEFAULT_SUBJECT_HEADER : subjectHeader.toString();
        versionHeaderName = versionHeader == null ? DEFAULT_VERSION_HEADER : versionHeader.toString();
        messageTypeHeaderName = messageTypeHeader == null ? DEFAULT_MESSAGE_TYPE_HEADER : messageTypeHeader.toString();
    }

    @Override
    public byte[] serialize(String topic, T data) {
        return data == null ? null : data.toByteArray();
    }

    @Override
    public byte[] serialize(String topic, Headers headers, T data) {
        if (data == null) {
            return null;
        }

        if (headers != null) {
            String subject = headerValue(headers, subjectHeaderName);
            if (subject == null) {
                subject = configuredSubject;
            }
            String version = headerValue(headers, versionHeaderName);
            if (version == null) {
                version = configuredVersion;
            }
            if (subject == null || subject.isBlank()) {
                throw new SerializationException(
                    "Missing protobuf subject. Set header '" + subjectHeaderName + "' or config '" + SUBJECT_CONFIG + "'."
                );
            }
            if (version == null || version.isBlank()) {
                throw new SerializationException(
                    "Missing protobuf schema version. Set header '" + versionHeaderName + "' or config '" + VERSION_CONFIG + "'."
                );
            }

            putHeader(headers, subjectHeaderName, subject);
            putHeader(headers, versionHeaderName, version);
            putHeader(headers, messageTypeHeaderName, data.getDescriptorForType().getFullName());
        }

        return data.toByteArray();
    }

    private String headerValue(Headers headers, String name) {
        if (headers.lastHeader(name) == null) {
            return null;
        }
        return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private void putHeader(Headers headers, String name, String value) {
        headers.remove(name);
        headers.add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }
}
