package org.acme;

import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import org.acme.dto.BuildPromptRequest;
import org.acme.dto.BuildPromptResponse;
import org.acme.dto.ForecastPersistRequest;
import org.acme.dto.ForecastPersistResponse;
import org.acme.entities.ForecastingResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;

@Path("/FlexibilityForecasting")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlexibilityForecastingResource {

    @Inject
    MySQLPool client;

    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void onStart(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS ForecastingResult").execute()
                .flatMap(r -> client.query(
                        "CREATE TABLE ForecastingResult (" +
                        "id SERIAL PRIMARY KEY, " +
                        "successRate FLOAT NOT NULL, " +
                        "dominantSentiment VARCHAR(20) NOT NULL, " +
                        "totalEventsAnalyzed INT NOT NULL, " +
                        "analyzedEventIds TEXT NOT NULL, " +
                        "createdAt DATETIME NOT NULL" +
                        ")").execute())
                .await().indefinitely();
    }

    @POST
    @Path("/build-prompt")
    public Response buildPrompt(BuildPromptRequest req) {
        String currentOutput = req.currentOutputKw != null
                ? String.format("%.2f kW (%s)", req.currentOutputKw,
                        req.currentOutputKw > 0 ? "discharging" : req.currentOutputKw < 0 ? "charging" : "idle")
                : "unknown";

        String prompt = String.format(
                "You are an energy grid analyst evaluating a VPP flexibility event.\n\n" +
                "Event #%d: Battery asset %d in %s was commanded to %s.\n" +
                "- SoC at event time: %.1f%% | SoH: %.1f%% | Market price: %s\n" +
                "- Current SoC: %.1f%% | Current output: %s | Status: %s\n\n" +
                "Did this command succeed? Respond ONLY with these exact lines, no other text:\n" +
                "SENTIMENT: POSITIVE or NEGATIVE or NEUTRAL\n" +
                "SUCCESS: YES or NO\n" +
                "REASONING: one short sentence",
                req.eventId, req.assetId, req.gridCellId,
                req.recommendedAction != null ? req.recommendedAction : req.eventType,
                nvl(req.socAtEventTime), nvl(req.sohAtEventTime),
                req.marketPriceLevel != null ? req.marketPriceLevel : "N/A",
                nvl(req.currentSoc), currentOutput,
                req.currentStatus != null ? req.currentStatus : "UNKNOWN");

        return Response.ok(new BuildPromptResponse(prompt)).build();
    }

    @POST
    @Path("/forecast")
    public Uni<Response> persistForecast(ForecastPersistRequest req) {
        ForecastingResult entity = new ForecastingResult();
        entity.successRate = req.successRate != null ? req.successRate : 0f;
        entity.dominantSentiment = req.dominantSentiment != null ? req.dominantSentiment : "NEUTRAL";
        entity.totalEventsAnalyzed = req.totalEventsAnalyzed != null ? req.totalEventsAnalyzed : 0;
        entity.analyzedEventIds = req.analyzedEventIds != null ? req.analyzedEventIds : "[]";
        entity.createdAt = LocalDateTime.now();
        return entity.save(client)
                .onItem().transform(id -> Response.ok(new ForecastPersistResponse(id)).build());
    }

    @GET
    @Path("/history")
    public Multi<ForecastingResult> getAllHistory() {
        return ForecastingResult.findAll(client);
    }

    @GET
    @Path("/history/{id}")
    public Uni<Response> getHistoryById(@PathParam("id") Long id) {
        return ForecastingResult.findById(client, id)
                .onItem().transform(result -> result != null
                        ? Response.ok(result)
                        : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    private float nvl(Float value) {
        return value != null ? value : 0f;
    }

    @DELETE
    @Path("/history/{id}")
    public Uni<Response> deleteHistory(@PathParam("id") Long id) {
        return ForecastingResult.delete(client, id)
                .onItem().transform(deleted -> deleted
                        ? Response.Status.NO_CONTENT
                        : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }
}
