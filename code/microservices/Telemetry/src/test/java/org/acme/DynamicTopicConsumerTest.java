package org.acme;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DynamicTopicConsumerTest {

    @Test
    void publishTelemetryEvent_sendsToBatteryEmitter() throws Exception {
        Emitter<String> batteryEmitter = Mockito.mock(Emitter.class);
        Emitter<String> solarEmitter = Mockito.mock(Emitter.class);
        Emitter<String> chargerEmitter = Mockito.mock(Emitter.class);

        DynamicTopicConsumer consumer = new DynamicTopicConsumer("Telemetry-Data", "localhost:9092", null,
                batteryEmitter, solarEmitter, chargerEmitter);

        invokePublishTelemetryEvent(consumer, "{\"asset_type\":\"BATTERY\"}", "A-1", "BATTERY");

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        Mockito.verify(batteryEmitter).send(captor.capture());
        Mockito.verifyNoInteractions(solarEmitter, chargerEmitter);

        Message message = captor.getValue();
        assertThat(message.getPayload().toString(), is("{\"asset_type\":\"BATTERY\"}"));
        Optional<OutgoingKafkaRecordMetadata> metadata = message.getMetadata(OutgoingKafkaRecordMetadata.class);
        assertThat(metadata.isPresent(), is(true));
        assertThat(metadata.get().getKey(), is("A-1"));
    }

    @Test
    void publishTelemetryEvent_sendsToSolarEmitter() throws Exception {
        Emitter<String> batteryEmitter = Mockito.mock(Emitter.class);
        Emitter<String> solarEmitter = Mockito.mock(Emitter.class);
        Emitter<String> chargerEmitter = Mockito.mock(Emitter.class);

        DynamicTopicConsumer consumer = new DynamicTopicConsumer("Telemetry-Data", "localhost:9092", null,
                batteryEmitter, solarEmitter, chargerEmitter);

        invokePublishTelemetryEvent(consumer, "{\"asset_type\":\"SOLAR\"}", "S-9", "SOLAR");

        Mockito.verify(solarEmitter).send(Mockito.any(Message.class));
        Mockito.verifyNoInteractions(batteryEmitter, chargerEmitter);
    }

    @Test
    void publishTelemetryEvent_sendsToChargerEmitter() throws Exception {
        Emitter<String> batteryEmitter = Mockito.mock(Emitter.class);
        Emitter<String> solarEmitter = Mockito.mock(Emitter.class);
        Emitter<String> chargerEmitter = Mockito.mock(Emitter.class);

        DynamicTopicConsumer consumer = new DynamicTopicConsumer("Telemetry-Data", "localhost:9092", null,
                batteryEmitter, solarEmitter, chargerEmitter);

        invokePublishTelemetryEvent(consumer, "{\"asset_type\":\"EV_CHARGER\"}", "C-2", "EV_CHARGER");

        Mockito.verify(chargerEmitter).send(Mockito.any(Message.class));
        Mockito.verifyNoInteractions(batteryEmitter, solarEmitter);
    }

    @Test
    void publishTelemetryEvent_unknownType_sendsNothing() throws Exception {
        Emitter<String> batteryEmitter = Mockito.mock(Emitter.class);
        Emitter<String> solarEmitter = Mockito.mock(Emitter.class);
        Emitter<String> chargerEmitter = Mockito.mock(Emitter.class);

        DynamicTopicConsumer consumer = new DynamicTopicConsumer("Telemetry-Data", "localhost:9092", null,
                batteryEmitter, solarEmitter, chargerEmitter);

        invokePublishTelemetryEvent(consumer, "{\"asset_type\":\"OTHER\"}", "X-0", "OTHER");

        Mockito.verifyNoInteractions(batteryEmitter, solarEmitter, chargerEmitter);
    }

    private void invokePublishTelemetryEvent(DynamicTopicConsumer consumer,
                                             String payload,
                                             String assetId,
                                             String assetType) throws Exception {
        Method method = DynamicTopicConsumer.class.getDeclaredMethod("publishTelemetryEvent", String.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(consumer, payload, assetId, assetType);
    }
}
