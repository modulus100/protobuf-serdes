package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import proto.it.v1.UserCreated;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SpringKafkaTestApplication.class)
class SpringKafkaAutoPayloadFormatIT {

    @Container
    static final ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.0.3"));

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", ProtobufDeserializer.class::getName);
        registry.add("spring.kafka.consumer.group-id", () -> "protobuf-serdes-auto-format-it-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG,
            UserCreated.class::getName
        );
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufDeserializer.PAYLOAD_FORMAT_CONFIG,
            () -> "auto"
        );
    }

    private static final String SCHEMA_REGISTRY_URL = "mock://protobuf-serdes-auto-format-it";

    @Autowired
    private ProbeConsumer probeConsumer;

    @Test
    void consumerConfiguredForAutoReadsRawAndConfluentPayloadFromDifferentProducers() throws Exception {
        UserCreated rawPayload = UserCreated.newBuilder()
            .setUserId("u-auto-format-raw-1")
            .setEmail("u-auto-format-raw-1@example.com")
            .setCreatedAtEpochMs(1_739_801_246_000L)
            .build();
        UserCreated confluentPayload = UserCreated.newBuilder()
            .setUserId("u-auto-format-confluent-1")
            .setEmail("u-auto-format-confluent-1@example.com")
            .setCreatedAtEpochMs(1_739_801_247_000L)
            .build();

        KafkaTemplate<String, UserCreated> rawKafkaTemplate = newRawKafkaTemplate();
        KafkaTemplate<String, UserCreated> confluentKafkaTemplate = newConfluentKafkaTemplate();
        try {
            rawKafkaTemplate.send(USER_CREATED_TOPIC, rawPayload.getUserId(), rawPayload).get(20, TimeUnit.SECONDS);
            confluentKafkaTemplate.send(USER_CREATED_TOPIC, confluentPayload.getUserId(), confluentPayload).get(20, TimeUnit.SECONDS);

            UserCreated first = probeConsumer.awaitMessage(Duration.ofSeconds(20));
            UserCreated second = probeConsumer.awaitMessage(Duration.ofSeconds(20));
            Set<UserCreated> consumed = new HashSet<>(Set.of(first, second));

            assertEquals(Set.of(rawPayload, confluentPayload), consumed);
        } finally {
            rawKafkaTemplate.destroy();
            confluentKafkaTemplate.destroy();
        }
    }

    private KafkaTemplate<String, UserCreated> newRawKafkaTemplate() {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
    }

    private KafkaTemplate<String, UserCreated> newConfluentKafkaTemplate() {
        Map<String, Object> producerProperties = new HashMap<>();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaProtobufSerializer.class);
        producerProperties.put("schema.registry.url", SCHEMA_REGISTRY_URL);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProperties));
    }
}
