package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;

import proto.it.v1.UserCreated;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SpringKafkaTestApplication.class)
class SpringKafkaProducerConsumerIT {

    @Container
    static final ConfluentKafkaContainer kafka =
        new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.0.3"));

    @DynamicPropertySource
    static void registerKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", ProtobufSerializer.class::getName);
        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", ProtobufDeserializer.class::getName);
        registry.add("spring.kafka.consumer.group-id", () -> "protobuf-serdes-it-group");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add(
            "spring.kafka.consumer.properties." + ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG,
            UserCreated.class::getName
        );
    }

    @Autowired
    private KafkaTemplate<String, UserCreated> kafkaTemplate;

    @Autowired
    private ProbeConsumer probeConsumer;

    @Test
    void producerAndConsumerRoundTripWithProtobufSerdes() throws Exception {
        UserCreated payload = UserCreated.newBuilder()
            .setUserId("u-integration-1")
            .setEmail("u-integration-1@example.com")
            .setCreatedAtEpochMs(1_739_801_238_000L)
            .build();

        kafkaTemplate.send(USER_CREATED_TOPIC, payload.getUserId(), payload).get(20, TimeUnit.SECONDS);

        UserCreated consumed = probeConsumer.awaitMessage(Duration.ofSeconds(20));
        assertEquals(payload, consumed);
    }
}
