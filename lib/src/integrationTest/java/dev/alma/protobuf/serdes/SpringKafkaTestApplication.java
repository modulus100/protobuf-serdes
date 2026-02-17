package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;

import proto.it.v1.UserCreated;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableKafka
public class SpringKafkaTestApplication {

    @Bean
    NewTopic userCreatedTopic() {
        return TopicBuilder.name(USER_CREATED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    ProducerFactory<String, UserCreated> producerFactory(
        @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(
            props,
            new StringSerializer(),
            new ProtobufSerializer<>()
        );
    }

    @Bean
    KafkaTemplate<String, UserCreated> kafkaTemplate(ProducerFactory<String, UserCreated> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, UserCreated> consumerFactory(
        @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "protobuf-serdes-it-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(
            props,
            new StringDeserializer(),
            new ProtobufDeserializer<>(UserCreated.parser())
        );
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, UserCreated> kafkaListenerContainerFactory(
        ConsumerFactory<String, UserCreated> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, UserCreated> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    @Bean
    ProbeConsumer probeConsumer() {
        return new ProbeConsumer();
    }
}
