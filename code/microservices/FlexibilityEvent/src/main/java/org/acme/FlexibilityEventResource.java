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
import org.eclipse.microprofile.rest.client.inject.RestClient;
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
    @RestClient
    TelemetryService telemetryService;

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

    @POST
    @Path("trigger")
    public Uni<Response> triggerFlexibilityAnalysis() {
        return telemetryService.getAllTelemetry()
            .collect().asList()
            .flatMap(telemetryList -> {
                int eventsGenerated = 0;
                for (TelemetryDTO t : telemetryList) {
                    if ("BATTERY".equals(t.asset_type)) { // FIXME: We should query the DB directly for battery
                        evaluateFlexibilityRules(t);
                        eventsGenerated++;
                    }
                }
                String message = String.format("Flexibility analysis completed. Analyzed %d battery assets.", eventsGenerated);
                return Uni.createFrom().item(Response.ok(message).build());
            });
    }

    private void evaluateFlexibilityRules(TelemetryDTO telemetry) {
        if (telemetry.State_of_Charge != null && telemetry.State_of_Charge > 90.0) {
            FlexibilityEvent event = new FlexibilityEvent();
            event.assetId = telemetry.asset_id;
            event.prosumerId = 1L;
            event.eventType = "ARBITRAGE_SELL";
            event.soc_percent = telemetry.State_of_Charge;
            event.recommendedAction = "DISCHARGE";
            event.marketPrice = getCurrentMarketPrice();
            event.incentiveAmount = calculateIncentive(telemetry.State_of_Charge);
            event.gridCellId = telemetry.grid_cell_id;
            event.timestamp = LocalDateTime.now();

            saveAndPublish(event);
        }

        if (telemetry.State_of_Charge != null && telemetry.State_of_Charge < 20.0) {
            FlexibilityEvent event = new FlexibilityEvent();
            event.assetId = telemetry.asset_id;
            event.prosumerId = 1L;
            event.eventType = "BALANCING_UNAVAILABLE";
            event.soc_percent = telemetry.State_of_Charge;
            event.recommendedAction = "UNAVAILABLE";
            event.gridCellId = telemetry.grid_cell_id;
            event.timestamp = LocalDateTime.now();

            saveAndPublish(event);
        }
    }

    private void saveAndPublish(FlexibilityEvent event) {
        event.save(client)
            .subscribe().with(
                eventId -> {
                    String kafkaMessage = String.format(
                        "{\"eventId\":%d,\"assetId\":%d,\"prosumerId\":%d,\"eventType\":\"%s\",\"recommendedAction\":\"%s\",\"timestamp\":\"%s\"}",
                        eventId, event.assetId, event.prosumerId, event.eventType, event.recommendedAction, event.timestamp
                    );
                    flexibilityEmitter.send(kafkaMessage);
                },
                failure -> System.err.println("Failed to save event: " + failure.getMessage())
            );
    }

    private Float getCurrentMarketPrice() {
        return 150.0f;
    }

    private Float calculateIncentive(Float soc) {
        return (soc - 90.0f) * 2.0f;
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
}
