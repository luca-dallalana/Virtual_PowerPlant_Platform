package org.acme;

import java.net.URI;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import java.time.LocalDateTime;

@Path("FlexibilityEvent")
public class FlexibilityEventResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    @Inject
    @Channel("flexibility-offers")
    Emitter<String> flexibilityEmitter;

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String kafka_servers;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS FlexibilityEvent").execute()
        .flatMap(r -> client.query("CREATE TABLE FlexibilityEvent (" +
                "id SERIAL PRIMARY KEY, " +
                "assetId BIGINT UNSIGNED NOT NULL, " +
                "prosumerId BIGINT UNSIGNED NOT NULL, " +
                "eventType VARCHAR(100) NOT NULL, " +
                "soc_percent FLOAT, " +
                "recommendedAction VARCHAR(50), " +
                "marketPrice FLOAT, " +
                "incentiveAmount FLOAT, " +
                "gridCellId VARCHAR(100), " +
                "timestamp DATETIME NOT NULL)").execute())
        .await().indefinitely();
    }

    @GET
    public Multi<FlexibilityEvent> get() {
        return FlexibilityEvent.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return FlexibilityEvent.findById(client, id)
                .onItem().transform(event -> event != null ? Response.ok(event) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("asset/{assetId}")
    public Multi<FlexibilityEvent> getByAsset(Long assetId) {
        return FlexibilityEvent.findByAssetId(client, assetId);
    }

    @GET
    @Path("type/{eventType}")
    public Multi<FlexibilityEvent> getByType(String eventType) {
        return FlexibilityEvent.findByEventType(client, eventType);
    }

    @POST
    @Path("evaluate")
    public Uni<Response> evaluateTelemetry(TelemetryDTO telemetry) {
        FlexibilityEvent event = null;

        if (telemetry.State_of_Charge != null && telemetry.State_of_Charge > 90.0) {
            event = new FlexibilityEvent();
            event.assetId = telemetry.asset_id;
            event.prosumerId = 1L;
            event.eventType = "ARBITRAGE_SELL";
            event.soc_percent = telemetry.State_of_Charge;
            event.recommendedAction = "DISCHARGE";
            event.marketPrice = getCurrentMarketPrice();
            event.incentiveAmount = calculateIncentive(telemetry.State_of_Charge);
            event.gridCellId = telemetry.grid_cell_id;
            event.timestamp = LocalDateTime.now();
        } else if (telemetry.State_of_Charge != null && telemetry.State_of_Charge < 20.0) {
            event = new FlexibilityEvent();
            event.assetId = telemetry.asset_id;
            event.prosumerId = 1L;
            event.eventType = "BALANCING_UNAVAILABLE";
            event.soc_percent = telemetry.State_of_Charge;
            event.recommendedAction = "UNAVAILABLE";
            event.gridCellId = telemetry.grid_cell_id;
            event.timestamp = LocalDateTime.now();
        }

        if (event != null) {
            final FlexibilityEvent finalEvent = event;
            return event.save(client)
                .onItem().transform(eventId -> {
                    finalEvent.id = eventId;
                    String kafkaMessage = String.format(
                        "{\"eventId\":%d,\"assetId\":%d,\"prosumerId\":%d,\"eventType\":\"%s\",\"recommendedAction\":\"%s\",\"timestamp\":\"%s\"}",
                        eventId, finalEvent.assetId, finalEvent.prosumerId, finalEvent.eventType, finalEvent.recommendedAction, finalEvent.timestamp
                    );
                    flexibilityEmitter.send(kafkaMessage);
                    System.out.printf("FlexibilityEvent created: assetId=%d, eventType=%s, soc=%.2f%%\n",
                        finalEvent.assetId, finalEvent.eventType, finalEvent.soc_percent);
                    return Response.ok(finalEvent).build();
                });
        } else {
            return Uni.createFrom().item(Response.status(204).build());
        }
    }

    private Float getCurrentMarketPrice() {
        return 150.0f;
    }

    private Float calculateIncentive(Float soc) {
        return (soc - 90.0f) * 2.0f;
    }
}
