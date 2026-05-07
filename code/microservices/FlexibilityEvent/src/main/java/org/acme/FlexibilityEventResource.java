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
import org.acme.consumers.TelemetryEventProcessor;

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
        Thread telemetryProcessor = new TelemetryEventProcessor(kafka_servers, client, flexibilityEmitter);
        telemetryProcessor.start();
        System.out.println("TelemetryEventProcessor started for FlexibilityEvent");
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
}
