package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableKafka
public class SpringKafkaTestApplication {

    @Bean
    NewTopic userCreatedTopic() {
        return TopicBuilder.name(USER_CREATED_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    ProbeConsumer probeConsumer() {
        return new ProbeConsumer();
    }
}
