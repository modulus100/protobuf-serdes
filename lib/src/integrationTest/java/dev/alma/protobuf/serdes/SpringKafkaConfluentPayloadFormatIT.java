package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import proto.it.v1.UserCreated;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SpringKafkaTestApplication.class)
class SpringKafkaConfluentPayloadFormatIT {

    private static final Network network = Network.newNetwork();

    @Container
    static final ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.0.3"))
            .withNetwork(network)
            .withListener("kafka:19092");

    @Container
    static final GenericContainer<?> schemaRegistry =
        new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:8.0.3"))
            .withNetwork(network)
            .withExposedPorts(8081)
            .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
            .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
            .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:19092")
            .dependsOn(kafka)
            .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", ProtobufDeserializer.class::getName);
        registry.add("spring.kafka.consumer.group-id", () -> "protobuf-serdes-confluent-format-it-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG,
            UserCreated.class::getName
        );
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufDeserializer.PAYLOAD_FORMAT_CONFIG,
            () -> "confluent"
        );
    }

    @Autowired
    private ProbeConsumer probeConsumer;

    @Test
    void consumerConfiguredForConfluentFormatReadsConfluentPayload() throws Exception {
        UserCreated payload = UserCreated.newBuilder()
            .setUserId("u-confluent-format-1")
            .setEmail("u-confluent-format-1@example.com")
            .setCreatedAtEpochMs(1_739_801_245_000L)
            .build();

        KafkaTemplate<String, UserCreated> confluentKafkaTemplate = newConfluentKafkaTemplate();
        try {
            confluentKafkaTemplate.send(USER_CREATED_TOPIC, payload.getUserId(), payload).get(20, TimeUnit.SECONDS);
        } finally {
            confluentKafkaTemplate.destroy();
        }

        UserCreated consumed = probeConsumer.awaitMessage(Duration.ofSeconds(20));
        assertEquals(payload, consumed);
    }

    private KafkaTemplate<String, UserCreated> newConfluentKafkaTemplate() {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        producerProperties.put("schema.registry.url", schemaRegistryUrl());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
    }

    private static String schemaRegistryUrl() {
        return "http://" + schemaRegistry.getHost() + ":" + schemaRegistry.getMappedPort(8081);
    }
}
