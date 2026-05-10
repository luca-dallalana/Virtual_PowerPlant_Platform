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
import org.acme.dto.ForecastRequest;
import org.acme.dto.ForecastResult;
import org.acme.entities.FlexibilityForecast;
import org.acme.services.ForecastingService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/FlexibilityForecasting")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FlexibilityForecastingResource {

    @Inject
    MySQLPool client;

    @Inject
    ForecastingService forecastingService;

    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void onStart(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS FlexibilityForecast").execute()
                .flatMap(r -> client.query("CREATE TABLE FlexibilityForecast ("
                        + "id SERIAL PRIMARY KEY, "
                        + "analysisType VARCHAR(50) NOT NULL, "
                        + "question TEXT NOT NULL, "
                        + "aiResponse TEXT NOT NULL, "
                        + "sentiment VARCHAR(20), "
                        + "confidenceScore DOUBLE, "
                        + "eventsAnalyzed INT, "
                        + "correlatedOutcomes INT, "
                        + "successRate DOUBLE, "
                        + "analysisTimestamp DATETIME NOT NULL, "
                        + "insightsJson TEXT, "
                        + "INDEX idx_analysis_type (analysisType), "
                        + "INDEX idx_timestamp (analysisTimestamp)"
                        + ")").execute())
                .await().indefinitely();
    }

    @POST
    @Path("/analyze")
    public Uni<ForecastResult> analyze(ForecastRequest request) {
        return forecastingService.performAnalysis(request);
    }

    @POST
    @Path("/analyze/sentiment")
    public Uni<ForecastResult> analyzeSentiment() {
        ForecastRequest request = new ForecastRequest();
        request.analysisType = "SENTIMENT";
        request.customQuestion = "What is the overall sentiment of flexibility operations?";
        return forecastingService.performAnalysis(request);
    }

    @POST
    @Path("/analyze/success-rate")
    public Uni<ForecastResult> analyzeSuccessRate(
            @QueryParam("eventType") String eventType,
            @QueryParam("recommendedAction") String recommendedAction) {
        ForecastRequest request = new ForecastRequest();
        request.analysisType = "SUCCESS_RATE";
        request.eventType = eventType;
        request.recommendedAction = recommendedAction;
        request.customQuestion = recommendedAction != null
                ? "Did the " + recommendedAction + " commands result in grid stability?"
                : "What is the success rate of flexibility actions?";
        return forecastingService.performAnalysis(request);
    }

    @GET
    @Path("/history")
    public Multi<FlexibilityForecast> getAllHistory() {
        return FlexibilityForecast.findAll(client);
    }

    @GET
    @Path("/history/{id}")
    public Uni<Response> getHistoryById(@PathParam("id") Long id) {
        return FlexibilityForecast.findById(client, id)
                .onItem().transform(forecast -> forecast != null ? Response.ok(forecast) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("/history/type/{analysisType}")
    public Multi<FlexibilityForecast> getHistoryByType(@PathParam("analysisType") String analysisType) {
        return FlexibilityForecast.findByAnalysisType(client, analysisType);
    }

    @DELETE
    @Path("/history/{id}")
    public Uni<Response> deleteHistory(@PathParam("id") Long id) {
        return FlexibilityForecast.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }
}
