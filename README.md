# protobuf-serdes

Minimal Kafka SerDes for protobuf messages, without Confluent serializer/deserializer dependencies.

## What this project provides

- `ProtobufSerializer<T extends MessageLite>`
- `ProtobufDeserializer<T extends MessageLite>`
- Unit tests with real protobuf messages generated from `src/test/proto`
- Spring Boot 4 + Testcontainers integration tests with protobuf from `src/integrationTest/proto`
- Published artifact contains only serde classes (test/integration generated classes are not packaged)

## Build and test

```bash
./gradlew :lib:test
```

Run integration tests (requires Docker):

```bash
./gradlew :lib:integrationTest
```

Regenerate test protobuf classes after proto changes:

```bash
./gradlew :lib:generateTestProto :lib:generateIntegrationTestProto
```

Run deserialization micro-benchmarks (JMH):

```bash
./gradlew :lib:jmh
```

## Spring Boot usage (Kafka)

Use generated protobuf parser in your consumer configuration:

```java
import dev.alma.protobuf.serdes.ProtobufDeserializer;
import dev.alma.protobuf.serdes.ProtobufSerializer;
import com.myteam.events.v1.UserCreated;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

var keyDeserializer = new StringDeserializer();
var valueDeserializer = new ProtobufDeserializer<UserCreated>();
var keySerializer = new StringSerializer();
var valueSerializer = new ProtobufSerializer<UserCreated>();

props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtobufDeserializer.class);
props.put(ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, "com.myteam.events.v1.UserCreated");
```

Spring Boot `application.yml` style:

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: dev.alma.protobuf.serdes.ProtobufSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: dev.alma.protobuf.serdes.ProtobufDeserializer
      group-id: my-group
      auto-offset-reset: earliest
      properties:
        protobuf.value.class: com.myteam.events.v1.UserCreated
```
