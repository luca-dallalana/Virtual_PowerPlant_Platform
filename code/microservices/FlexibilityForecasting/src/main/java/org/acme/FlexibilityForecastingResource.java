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
import org.acme.dto.*;
import org.acme.entities.ForecastingResult;
import org.acme.services.DataCorrelationService;
import org.acme.services.PromptBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;

@Path("/FlexibilityForecasting")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlexibilityForecastingResource {

    @Inject
    MySQLPool client;

    @Inject
    DataCorrelationService correlationService;

    @Inject
    PromptBuilder promptBuilder;

    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void onStart(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS ForecastingResult").execute()
                .flatMap(r -> client.query("CREATE TABLE ForecastingResult ("
                        + "id SERIAL PRIMARY KEY, "
                        + "forecastResult TEXT NOT NULL, "
                        + "windowStart VARCHAR(50), "
                        + "windowEnd VARCHAR(50), "
                        + "flexibilityEventsCount INT, "
                        + "gridBalancingCount INT, "
                        + "createdAt DATETIME NOT NULL"
                        + ")").execute())
                .await().indefinitely();
    }

    @POST
    @Path("/evaluate-correlation")
    public Response evaluateCorrelation(EventCorrelationRequest request) {
        EventCorrelationResult result = correlationService.buildResult(request);
        return Response.ok(result).build();
    }

    @POST
    @Path("/build-prompt")
    public Response buildPrompt(EventCorrelationResult correlationResult) {
        String prompt = promptBuilder.buildPrompt(correlationResult);
        return Response.ok(new OllamaPromptResult(prompt)).build();
    }

    @POST
    @Path("/forecast")
    public Uni<Response> persistForecast(ForecastPersistRequest request) {
        ForecastingResult entity = new ForecastingResult();
        entity.forecastResult = request.forecastResult;
        entity.windowStart = request.windowStart;
        entity.windowEnd = request.windowEnd;
        entity.flexibilityEventsCount = request.flexibilityEventsCount;
        entity.gridBalancingCount = request.gridBalancingCount;
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
                .onItem().transform(result -> result != null ? Response.ok(result) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @DELETE
    @Path("/history/{id}")
    public Uni<Response> deleteHistory(@PathParam("id") Long id) {
        return ForecastingResult.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }
}
