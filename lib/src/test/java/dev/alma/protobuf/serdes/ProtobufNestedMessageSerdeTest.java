package dev.alma.protobuf.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import proto.test.v1.OrderEnvelope;
import org.junit.jupiter.api.Test;

class ProtobufNestedMessageSerdeTest {

    @Test
    void roundTripsNestedProtobufMessage() {
        ProtobufSerializer<OrderEnvelope> serializer = new ProtobufSerializer<>();
        ProtobufDeserializer<OrderEnvelope> deserializer = new ProtobufDeserializer<>();
        deserializer.configure(
            Map.of(ProtobufDeserializer.VALUE_CLASS_NAME_CONFIG, OrderEnvelope.class.getName()),
            false
        );

        OrderEnvelope payload = OrderEnvelope.newBuilder()
            .setEventId("evt-1001")
            .setCreatedAtEpochMs(1_739_801_243_000L)
            .setCustomer(
                OrderEnvelope.Customer.newBuilder()
                    .setCustomerId("cust-1")
                    .setEmail("cust-1@example.com")
                    .setShippingAddress(
                        OrderEnvelope.Address.newBuilder()
                            .setLine1("1 Main Street")
                            .setCity("Riga")
                            .setCountry("LV")
                            .build()
                    )
                    .build()
            )
            .addItems(
                OrderEnvelope.LineItem.newBuilder()
                    .setSku("SKU-RED-42")
                    .setQuantity(2)
                    .setUnitPriceCents(1599)
                    .build()
            )
            .setMetadata(
                OrderEnvelope.Metadata.newBuilder()
                    .setSource("integration-test")
                    .setTraceId("trace-123")
                    .build()
            )
            .build();

        byte[] bytes = serializer.serialize("orders", payload);
        OrderEnvelope deserialized = deserializer.deserialize("orders", bytes);

        assertEquals(payload, deserialized);
    }
}
