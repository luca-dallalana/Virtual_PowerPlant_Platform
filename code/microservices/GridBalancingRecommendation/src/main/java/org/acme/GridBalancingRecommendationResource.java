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
import org.acme.dto.BalancingRecommendationDTO;
import org.acme.dto.GridCellMetricsDTO;
import org.acme.dto.GridCellMetricsRequest;
import org.acme.entities.BalancingRecommendation;
import org.acme.services.GridBalancingRecommendationService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

    void onStart(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        final String INS = "INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType) VALUES ";

        client.query("DROP TABLE IF EXISTS BalancingRecommendation").execute()
                .flatMap(r -> client.query("CREATE TABLE BalancingRecommendation ("
                        + "id SERIAL PRIMARY KEY, "
                        + "assetId BIGINT NOT NULL, "
                        + "action VARCHAR(50) NOT NULL, "
                        + "fromCell VARCHAR(100) NOT NULL, "
                        + "toCell VARCHAR(100) NOT NULL, "
                        + "createdAt DATETIME NOT NULL, "
                        + "cellContext VARCHAR(20) NOT NULL, "
                        + "socPercent FLOAT NULL, "
                        + "isCharging TINYINT(1) NOT NULL, "
                        + "assetType VARCHAR(50) NOT NULL"
                        + ")").execute())
                // Seed: PORTO-IN overload scenario — EV chargers in PORTO-IN recommended to reduce load
                .flatMap(r -> client.query(INS + "(1006,'REDUCE_CHARGING','PORTO-IN','PORTO-IN',NOW()-INTERVAL 15 MINUTE,'STRESSED',NULL,1,'EV_CHARGER')").execute())
                .flatMap(r -> client.query(INS + "(1007,'REDUCE_CHARGING','PORTO-IN','PORTO-IN',NOW()-INTERVAL 15 MINUTE,'STRESSED',NULL,1,'EV_CHARGER')").execute())
                // LISBON-DT battery discharging to supply PORTO-IN
                .flatMap(r -> client.query(INS + "(1001,'DISCHARGE','LISBON-DT','PORTO-IN',NOW()-INTERVAL 15 MINUTE,'SURPLUS',75.0,0,'BATTERY')").execute())
                // 13 minutes ago: second evaluation cycle
                .flatMap(r -> client.query(INS + "(1006,'REDUCE_CHARGING','PORTO-IN','PORTO-IN',NOW()-INTERVAL 13 MINUTE,'STRESSED',NULL,1,'EV_CHARGER')").execute())
                .flatMap(r -> client.query(INS + "(1007,'REDUCE_CHARGING','PORTO-IN','PORTO-IN',NOW()-INTERVAL 13 MINUTE,'STRESSED',NULL,1,'EV_CHARGER')").execute())
                .await().indefinitely();
    }

    @POST
    @Path("/metrics")
    public Response computeMetrics(GridCellMetricsRequest request) {
        if (request.gridCell != null) {
            GridCellMetricsDTO result = recommendationService.computeSingleCellMetrics(
                    request.gridCell, request.telemetryData);
            return Response.ok(result).build();
        } else if (request.neighbourCells != null) {
            List<GridCellMetricsDTO> results = recommendationService.computeMultiCellMetrics(
                    request.neighbourCells, request.allTelemetry);
            return Response.ok(results).build();
        }
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Request must contain either gridCell or neighbourCells").build();
    }

    @POST
    @Path("/save")
    public Uni<Response> saveRecommendations(List<BalancingRecommendationDTO> recommendations) {
        return recommendationService.saveRecommendations(recommendations)
                .onItem().transform(saved -> Response.ok(saved).build());
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
    @Path("source/{fromCell}")
    public Multi<BalancingRecommendation> getBySource(@PathParam("fromCell") String fromCell) {
        return BalancingRecommendation.findByFromCell(client, fromCell);
    }

    @GET
    @Path("recommendations/{minutes}")
    public Multi<BalancingRecommendation> getByMinutes(@PathParam("minutes") int minutes) {
        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = toTime.minusMinutes(minutes);
        return BalancingRecommendation.findByTimeWindow(client, fromTime, toTime);
    }

    @POST
    public Uni<Response> create(BalancingRecommendation recommendation) {
        if (recommendation.createdAt == null) {
            recommendation.createdAt = LocalDateTime.now();
        }
        return recommendation.save(client)
                .onItem().transform(id -> URI.create("/GridBalancingRecommendation/" + id))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @PUT
    @Path("{id}")
    public Uni<Response> update(@PathParam("id") Long id, BalancingRecommendation recommendation) {
        if (recommendation.createdAt == null) {
            recommendation.createdAt = LocalDateTime.now();
        }
        return BalancingRecommendation.update(client, id,
                        recommendation.assetId,
                        recommendation.action,
                        recommendation.fromCell,
                        recommendation.toCell,
                        recommendation.createdAt,
                        recommendation.cellContext,
                        recommendation.socPercent,
                        recommendation.isCharging,
                        recommendation.assetType)
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
}
