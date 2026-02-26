# protobuf-serdes

Minimal Kafka SerDes for protobuf messages, without Confluent serializer/deserializer dependencies.

## What this project provides

- `ProtobufSerializer<T extends MessageLite>`
- `ProtobufDeserializer<T extends MessageLite>`
- `ProtobufS3Serializer<T extends Message>`
- `ProtobufS3Deserializer<T extends Message>`
- `ProtobufDeserializer` payload format modes: `raw`, `confluent`, `auto`
- Unit tests with real protobuf messages generated from `src/test/proto`
- Spring Boot 4 + Testcontainers integration tests with protobuf from `src/integrationTest/proto` (including `raw`, `confluent`, and `auto` payload-format scenarios)
- Published artifact contains only serde classes (test/integration generated classes are not packaged)

## Build and test

```bash
./gradlew :lib:test
```

Run integration tests (requires Docker):

```bash
./gradlew :lib:integrationTest
```

Run optional load/soak integration test (disabled by default):

```bash
./gradlew :lib:integrationTest \
  -Dsoak.tests=true \
  -Dsoak.messages=20000 \
  -Dsoak.producer.threads=8 \
  -Dsoak.listener.concurrency=6 \
  -Dsoak.consume.timeout.seconds=180
```

Regenerate integration descriptor file from proto with Buf:

```bash
buf build lib/src/integrationTest/proto \
  --as-file-descriptor-set \
  -o lib/src/integrationTest/resources/descriptors/user_created_v1.desc
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
        protobuf.payload.format: raw # raw | confluent | auto
```

`protobuf.payload.format` options:

- `raw` (default): parse message bytes directly as protobuf.
- `auto` (default): accept both raw protobuf payload and Confluent wire payload.
- `raw`: parse message bytes directly as protobuf.
- `confluent`: parse Confluent wire payload (magic byte + schema id + message-indexes + protobuf bytes).

## Spring Boot usage (S3-backed descriptors)

`ProtobufS3Serializer` writes protobuf bytes and sets Kafka headers:
- `protobuf.subject`
- `protobuf.schema.version`
- `protobuf.message.type`

`ProtobufS3Deserializer` reads those headers, loads descriptor-set bytes from S3, caches schema descriptors using Guava cache, and deserializes into configured message class (or `DynamicMessage` when class is not configured).
Descriptor bytes should be a Buf-generated file descriptor set (`buf build --as-file-descriptor-set`).

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: dev.alma.protobuf.serdes.ProtobufS3Serializer
      properties:
        protobuf.s3.subject: com.myteam.events.user-created
        protobuf.s3.version: 1
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: dev.alma.protobuf.serdes.ProtobufS3Deserializer
      group-id: my-group
      auto-offset-reset: earliest
      properties:
        protobuf.s3.value.class: com.myteam.events.v1.UserCreated
        protobuf.s3.bucket: my-protobuf-descriptors
        protobuf.s3.region: us-east-1
        protobuf.s3.access.key: your-access-key # optional (for LocalStack or static creds)
        protobuf.s3.secret.key: your-secret-key # optional (for LocalStack or static creds)
        protobuf.s3.key.template: "{subject}/{version}/descriptor.pb"
        protobuf.s3.cache.max.size: 1000
        protobuf.s3.cache.ttl.seconds: 3600
```
