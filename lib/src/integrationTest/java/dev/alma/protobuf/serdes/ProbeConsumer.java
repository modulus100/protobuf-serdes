package dev.alma.protobuf.serdes;

import static dev.alma.protobuf.serdes.ProtobufTestTopics.USER_CREATED_TOPIC;

import proto.it.v1.UserCreated;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.kafka.annotation.KafkaListener;

public final class ProbeConsumer {

    private final BlockingQueue<UserCreated> messages = new LinkedBlockingQueue<>();
    private final AtomicInteger consumedCount = new AtomicInteger();
    private volatile CountDownLatch batchLatch = new CountDownLatch(0);

    @KafkaListener(
        topics = USER_CREATED_TOPIC,
        groupId = "protobuf-serdes-it-listener",
        concurrency = "${it.kafka.listener.concurrency:1}"
    )
    void onMessage(UserCreated message) {
        messages.offer(message);
        consumedCount.incrementAndGet();
        CountDownLatch latch = batchLatch;
        if (latch.getCount() > 0) {
            latch.countDown();
        }
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

    void prepareForBatch(int expectedMessages) {
        messages.clear();
        consumedCount.set(0);
        batchLatch = new CountDownLatch(expectedMessages);
    }

    int awaitBatch(Duration timeout) {
        try {
            boolean completed = batchLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                throw new AssertionError(
                    "Expected " + batchLatch.getCount() + " more consumed messages within " + timeout
                );
            }
            int consumed = consumedCount.get();
            messages.clear();
            return consumed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for consumed batch", e);
        }
    }
}
