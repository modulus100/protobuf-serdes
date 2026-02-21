package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import proto.it.v1.UserCreated;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SpringKafkaTestApplication.class)
class SpringKafkaS3ProducerConsumerIT {

    private static final String SUBJECT = "proto.it.v1.user-created";
    private static final String VERSION = "1";
    private static final String ALT_SUBJECT = "proto.it.v1.user-created-alt";
    private static final String ALT_VERSION = "2";
    private static final String BUCKET = "protobuf-descriptors-it";
    private static final String KEY_TEMPLATE = "{subject}/{version}/descriptor.pb";
    private static final String DESCRIPTOR_RESOURCE = "descriptors/user_created_v1.desc";

    @Container
    static final ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.0.3"));

    @Container
    static final LocalStackContainer localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.S3);

    @BeforeAll
    static void uploadDescriptorToS3() {
        try (S3Client s3Client = S3Client.builder()
            .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                )
            )
            .region(Region.of(localstack.getRegion()))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(KEY_TEMPLATE.replace("{subject}", SUBJECT).replace("{version}", VERSION))
                    .build(),
                RequestBody.fromBytes(loadDescriptorBytes())
            );
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(BUCKET)
                    .key(KEY_TEMPLATE.replace("{subject}", ALT_SUBJECT).replace("{version}", ALT_VERSION))
                    .build(),
                RequestBody.fromBytes(loadDescriptorBytes())
            );
        }
    }

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", ProtobufS3Serializer.class::getName);
        registry.add("spring.kafka.producer.properties." + ProtobufS3Serializer.SUBJECT_CONFIG, () -> SUBJECT);
        registry.add("spring.kafka.producer.properties." + ProtobufS3Serializer.VERSION_CONFIG, () -> VERSION);

        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", ProtobufS3Deserializer.class::getName);
        registry.add("spring.kafka.consumer.group-id", () -> "protobuf-serdes-s3-it-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufS3Deserializer.VALUE_CLASS_NAME_CONFIG,
            UserCreated.class::getName
        );
        registry.add("spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_BUCKET_CONFIG, () -> BUCKET);
        registry.add("spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_REGION_CONFIG, localstack::getRegion);
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_ENDPOINT_CONFIG,
            () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString()
        );
        registry.add("spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_PATH_STYLE_ACCESS_CONFIG, () -> "true");
        registry.add("spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_ACCESS_KEY_CONFIG, localstack::getAccessKey);
        registry.add("spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_SECRET_KEY_CONFIG, localstack::getSecretKey);
        registry.add("spring.kafka.consumer.properties." + ProtobufS3Deserializer.S3_KEY_TEMPLATE_CONFIG, () -> KEY_TEMPLATE);
    }

    @Autowired
    private KafkaTemplate<String, UserCreated> kafkaTemplate;

    @Autowired
    private ProbeConsumer probeConsumer;

    @Test
    void producerAndConsumerRoundTripWithS3BackedProtobufSerdes() throws Exception {
        UserCreated payload = UserCreated.newBuilder()
            .setUserId("u-s3-it-1")
            .setEmail("u-s3-it-1@example.com")
            .setCreatedAtEpochMs(1_739_801_242_000L)
            .build();

        kafkaTemplate.send(USER_CREATED_TOPIC, payload.getUserId(), payload).get(20, TimeUnit.SECONDS);

        UserCreated consumed = probeConsumer.awaitMessage(Duration.ofSeconds(20));
        assertEquals(payload, consumed);
    }

    @Test
    void producerCanOverrideSubjectAndVersionPerMessageViaHeaders() throws Exception {
        UserCreated payload = UserCreated.newBuilder()
            .setUserId("u-s3-it-2")
            .setEmail("u-s3-it-2@example.com")
            .setCreatedAtEpochMs(1_739_801_243_000L)
            .build();

        ProducerRecord<String, UserCreated> record = new ProducerRecord<>(USER_CREATED_TOPIC, payload.getUserId(), payload);
        record.headers().add("protobuf.subject", ALT_SUBJECT.getBytes(StandardCharsets.UTF_8));
        record.headers().add("protobuf.schema.version", ALT_VERSION.getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).get(20, TimeUnit.SECONDS);

        UserCreated consumed = probeConsumer.awaitMessage(Duration.ofSeconds(20));
        assertEquals(payload, consumed);
    }

    @Test
    void producerCanSendSpringMessageBuilderWithProtobufHeaders() throws Exception {
        UserCreated payload = UserCreated.newBuilder()
            .setUserId("u-s3-it-3")
            .setEmail("u-s3-it-3@example.com")
            .setCreatedAtEpochMs(1_739_801_244_000L)
            .build();

        Message<UserCreated> message = MessageBuilder.withPayload(payload)
            .setHeader(KafkaHeaders.TOPIC, USER_CREATED_TOPIC)
            .setHeader(KafkaHeaders.KEY, payload.getUserId())
            .setHeader("protobuf.subject", ALT_SUBJECT.getBytes(StandardCharsets.UTF_8))
            .setHeader("protobuf.schema.version", ALT_VERSION.getBytes(StandardCharsets.UTF_8))
            .build();

        kafkaTemplate.send(message).get(20, TimeUnit.SECONDS);

        UserCreated consumed = probeConsumer.awaitMessage(Duration.ofSeconds(20));
        assertEquals(payload, consumed);
    }

    private static byte[] loadDescriptorBytes() {
        try (InputStream input = SpringKafkaS3ProducerConsumerIT.class.getClassLoader().getResourceAsStream(DESCRIPTOR_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Descriptor resource not found: " + DESCRIPTOR_RESOURCE);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read descriptor resource: " + DESCRIPTOR_RESOURCE, e);
        }
    }
}
