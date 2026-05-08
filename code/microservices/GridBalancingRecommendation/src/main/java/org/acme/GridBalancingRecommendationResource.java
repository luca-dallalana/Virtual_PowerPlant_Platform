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
import org.acme.entities.BalancingRecommendation;
import org.acme.services.GridBalancingRecommendationService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.acme.dto.GridBalancingRequest;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@Path("/GridBalancingRecommendation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GridBalancingRecommendationResource {

    @Inject
    MySQLPool client;

    @Inject
    GridBalancingRecommendationService recommendationService;

    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    @ConfigProperty(name = "gridbalancing.threshold.percent", defaultValue = "0.9")
    double thresholdPercent;

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String kafka_servers;

    void onStart(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS BalancingRecommendation").execute()
                .flatMap(r -> client.query("CREATE TABLE BalancingRecommendation ("
                        + "id SERIAL PRIMARY KEY, "
                        + "sourceGridCellId VARCHAR(100) NOT NULL, "
                        + "targetGridCellId VARCHAR(100), "
                        + "sourceNetLoadKw DOUBLE NOT NULL, "
                        + "targetHeadroomKw DOUBLE, "
                        + "overloadKw DOUBLE NOT NULL, "
                        + "transferableKw DOUBLE, "
                        + "thresholdPercent DOUBLE NOT NULL, "
                        + "status VARCHAR(30) NOT NULL, "
                        + "rationale TEXT, "
                        + "createdAt DATETIME NOT NULL"
                        + ")").execute())
                .await().indefinitely();
    }

    @POST
    @Path("/evaluate")
    public Uni<Response> evaluate(GridBalancingRequest request) {
        return recommendationService.calculateRecommendationsFromEvents(
            request.telemetryData,
            request.gridCells
        ).onItem().transform(recommendations -> Response.ok(recommendations).build());
    }

    @GET
    public Multi<BalancingRecommendation> getAll() {
        return BalancingRecommendation.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getById(@PathParam("id") Long id) {
        return BalancingRecommendation.findById(client, id)
                .onItem().transform(rec -> rec != null ? Response.ok(rec) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("source/{gridCellId}")
    public Multi<BalancingRecommendation> getBySource(@PathParam("gridCellId") String gridCellId) {
        return BalancingRecommendation.findBySourceGridCellId(client, gridCellId);
    }

    @POST
    public Uni<Response> create(BalancingRecommendation recommendation) {
        applyDefaults(recommendation);
        return recommendation.save(client)
                .onItem().transform(id -> URI.create("/GridBalancingRecommendation/" + id))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @PUT
    @Path("{id}")
    public Uni<Response> update(@PathParam("id") Long id, BalancingRecommendation recommendation) {
        applyDefaults(recommendation);
        return BalancingRecommendation.update(client,
                        id,
                        recommendation.sourceGridCellId,
                        recommendation.targetGridCellId,
                        recommendation.sourceNetLoadKw,
                        recommendation.targetHeadroomKw,
                        recommendation.overloadKw,
                        recommendation.transferableKw,
                        recommendation.thresholdPercent,
                        recommendation.status,
                        recommendation.rationale,
                        recommendation.createdAt)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @DELETE
    @Path("{id}")
    public Uni<Response> delete(@PathParam("id") Long id) {
        return BalancingRecommendation.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    private void applyDefaults(BalancingRecommendation recommendation) {
        if (recommendation.createdAt == null) {
            recommendation.createdAt = LocalDateTime.now();
        }
        if (recommendation.thresholdPercent == null) {
            recommendation.thresholdPercent = thresholdPercent;
        }
        if (recommendation.status == null || recommendation.status.isBlank()) {
            recommendation.status = "MANUAL";
        }
    }
}
