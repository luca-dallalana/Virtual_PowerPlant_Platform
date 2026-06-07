package org.acme;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Path("FlexibilityEvent")
public class FlexibilityEventResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        final String INS = "INSERT INTO FlexibilityEvent "
            + "(assetId,prosumerId,eventType,soc_percent,soh_percent,recommendedAction,marketPriceLevel,gridCellId,timestamp) VALUES ";

        client.query("DROP TABLE IF EXISTS FlexibilityEvent").execute()
        .flatMap(r -> client.query("CREATE TABLE FlexibilityEvent (" +
                "id SERIAL PRIMARY KEY, " +
                "assetId BIGINT UNSIGNED NOT NULL, " +
                "prosumerId BIGINT UNSIGNED NOT NULL, " +
                "eventType VARCHAR(100) NOT NULL, " +
                "soc_percent FLOAT, " +
                "soh_percent FLOAT, " +
                "recommendedAction VARCHAR(50), " +
                "marketPriceLevel VARCHAR(10), " +
                "gridCellId VARCHAR(100), " +
                "timestamp DATETIME NOT NULL)").execute())
        // Historical flexibility events — simulate prior BPMN runs for FlexibilityForecasting context.
        .flatMap(r -> client.query(INS + "(1001,1,'ARBITRAGE_SELL',95.2,92.5,'DISCHARGE','HIGH','LISBON-DT',NOW()-INTERVAL 18 MINUTE)").execute())
        .flatMap(r -> client.query(INS + "(1001,1,'ARBITRAGE_SELL',93.1,92.5,'DISCHARGE','HIGH','LISBON-DT',NOW()-INTERVAL 14 MINUTE)").execute())
        .flatMap(r -> client.query(INS + "(1011,2,'ARBITRAGE_SELL',96.8,88.0,'DISCHARGE','HIGH','SETUBAL-CT',NOW()-INTERVAL 12 MINUTE)").execute())
        .flatMap(r -> client.query(INS + "(1008,4,'BALANCING_UNAVAILABLE',17.3,75.0,'UNAVAILABLE',NULL,'FARO-RS',NOW()-INTERVAL 10 MINUTE)").execute())
        .flatMap(r -> client.query(INS + "(1001,1,'ARBITRAGE_SELL',91.5,92.5,'DISCHARGE','HIGH','LISBON-DT',NOW()-INTERVAL 6 MINUTE)").execute())
        .flatMap(r -> client.query(INS + "(1011,2,'ARBITRAGE_SELL',94.2,88.0,'DISCHARGE','HIGH','SETUBAL-CT',NOW()-INTERVAL 3 MINUTE)").execute())
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

    @GET
    @Path("logs/{minutes}")
    public Multi<FlexibilityEvent> getLogsByMinutes(@PathParam("minutes") int minutes) {
        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = toTime.minusMinutes(minutes);
        return FlexibilityEvent.findByTimeWindow(client, fromTime, toTime);
    }

    @POST
    @Path("save")
    public Uni<Response> saveBatch(List<FlexibilityEvent> events) {
        if (events == null || events.isEmpty()) {
            return Uni.createFrom().item(Response.ok(new ArrayList<>()).build());
        }

        for (FlexibilityEvent event : events) {
            if (event.timestamp == null) {
                event.timestamp = LocalDateTime.now();
            }
        }

        List<Uni<Long>> saveUnis = events.stream()
            .map(event -> event.save(client))
            .collect(Collectors.toList());

        return Uni.combine().all().unis(saveUnis).combinedWith(ids -> {
            for (int i = 0; i < events.size(); i++) {
                events.get(i).id = (Long) ids.get(i);
            }
            return Response.ok(events).build();
        });
    }
}
