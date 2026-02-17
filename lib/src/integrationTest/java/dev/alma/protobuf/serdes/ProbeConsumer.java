package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;

import proto.it.v1.UserCreated;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.annotation.KafkaListener;

public final class ProbeConsumer {

    private final BlockingQueue<UserCreated> messages = new LinkedBlockingQueue<>();

    @KafkaListener(topics = USER_CREATED_TOPIC, groupId = "protobuf-serdes-it-listener")
    void onMessage(UserCreated message) {
        messages.offer(message);
    }

    UserCreated awaitMessage(Duration timeout) {
        try {
            UserCreated message = messages.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (message == null) {
                throw new AssertionError("Expected a consumed message within " + timeout);
            }
            return message;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for consumed message", e);
        }
    }
}
