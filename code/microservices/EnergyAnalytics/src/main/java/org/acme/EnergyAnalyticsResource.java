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
import org.acme.dto.*;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;
import org.acme.services.AnalyticsCalculationService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/EnergyAnalytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EnergyAnalyticsResource {

    @Inject
    MySQLPool client;

    @Inject
    AnalyticsCalculationService analyticsService;

    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    void onStart(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS EnergyDischargedByZone").execute()
            .flatMap(r -> client.query("DROP TABLE IF EXISTS GeneratedEnergyByProsumer").execute())
            .flatMap(r -> client.query("DROP TABLE IF EXISTS ConsumedEnergyByProsumer").execute())
            .flatMap(r -> client.query("DROP TABLE IF EXISTS AverageSoC").execute())
            .flatMap(r -> client.query("CREATE TABLE EnergyDischargedByZone (" +
                "id SERIAL PRIMARY KEY, " +
                "gridCellId VARCHAR(100) NOT NULL, " +
                "totalEnergyDischargedKwh DOUBLE NOT NULL, " +
                "batteryCount INT NOT NULL, " +
                "timestamp DATETIME NOT NULL, " +
                "aggregationPeriod VARCHAR(20) NOT NULL" +
                ")").execute())
            .flatMap(r -> client.query("CREATE TABLE GeneratedEnergyByProsumer (" +
                "id SERIAL PRIMARY KEY, " +
                "prosumerId BIGINT NOT NULL, " +
                "totalEnergyGeneratedKwh DOUBLE NOT NULL, " +
                "solarAssetCount INT NOT NULL, " +
                "timestamp DATETIME NOT NULL, " +
                "aggregationPeriod VARCHAR(20) NOT NULL" +
                ")").execute())
            .flatMap(r -> client.query("CREATE TABLE ConsumedEnergyByProsumer (" +
                "id SERIAL PRIMARY KEY, " +
                "prosumerId BIGINT NOT NULL, " +
                "totalEnergyConsumedKwh DOUBLE NOT NULL, " +
                "evChargerCount INT NOT NULL, " +
                "timestamp DATETIME NOT NULL, " +
                "aggregationPeriod VARCHAR(20) NOT NULL" +
                ")").execute())
            .flatMap(r -> client.query("CREATE TABLE AverageSoC (" +
                "id SERIAL PRIMARY KEY, " +
                "averageSocPercent DOUBLE NOT NULL, " +
                "batteryCount INT NOT NULL, " +
                "timestamp DATETIME NOT NULL, " +
                "aggregationPeriod VARCHAR(20) NOT NULL" +
                ")").execute())
            .await().indefinitely();
    }

    @POST
    @Path("/compute/generated-by-prosumer")
    public Response computeGeneratedByProsumer(ComputeGeneratedRequest request) {
        return Response.ok(analyticsService.computeGeneratedByProsumer(request.assets, request.telemetry)).build();
    }

    @POST
    @Path("/compute/consumed-by-prosumer")
    public Response computeConsumedByProsumer(ComputeConsumedRequest request) {
        return Response.ok(analyticsService.computeConsumedByProsumer(request.assets, request.telemetry)).build();
    }

    @POST
    @Path("/compute/discharged-by-zone")
    public Response computeDischargedByZone(ComputeDischargedRequest request) {
        return Response.ok(analyticsService.computeDischargedByZone(request.zones, request.telemetry)).build();
    }

    @POST
    @Path("/compute/average-soc")
    public Response computeAverageSoC(ComputeAverageSoCRequest request) {
        return Response.ok(analyticsService.computeAverageSoC(request.telemetry)).build();
    }

    @POST
    @Path("/persist/consume")
    public Uni<Response> persistConsumed(PersistConsumedRequest request) {
        return analyticsService.persistConsumed(request.consumedByProsumer)
                .onItem().transform(result -> Response.ok(result).build());
    }

    @POST
    @Path("/persist/generate")
    public Uni<Response> persistGenerated(PersistGeneratedRequest request) {
        return analyticsService.persistGenerated(request.generatedByProsumer)
                .onItem().transform(result -> Response.ok(result).build());
    }

    @POST
    @Path("/persist/discharge")
    public Uni<Response> persistDischarged(PersistDischargedRequest request) {
        return analyticsService.persistDischarged(request.dischargedByZone)
                .onItem().transform(result -> Response.ok(result).build());
    }

    @POST
    @Path("/persist/average")
    public Uni<Response> persistAverage(PersistAverageSoCRequest request) {
        return analyticsService.persistAverageSoC(request.averageSoC)
                .onItem().transform(result -> Response.ok(result).build());
    }

    @GET
    @Path("/discharged-by-zone")
    public Multi<EnergyDischargedByZone> getDischargedByZone() {
        return EnergyDischargedByZone.findAll(client);
    }

    @GET
    @Path("/discharged-by-zone/{gridCellId}")
    public Multi<EnergyDischargedByZone> getDischargedByGridCell(@PathParam("gridCellId") String gridCellId) {
        return EnergyDischargedByZone.findByGridCellId(client, gridCellId);
    }

    @GET
    @Path("/generated-by-prosumer")
    public Multi<GeneratedEnergyByProsumer> getGeneratedByProsumer() {
        return GeneratedEnergyByProsumer.findAll(client);
    }

    @GET
    @Path("/generated-by-prosumer/{prosumerId}")
    public Multi<GeneratedEnergyByProsumer> getGeneratedByProsumerId(@PathParam("prosumerId") Long prosumerId) {
        return GeneratedEnergyByProsumer.findByProsumerId(client, prosumerId);
    }

    @GET
    @Path("/consumed-by-prosumer")
    public Multi<ConsumedEnergyByProsumer> getConsumedByProsumer() {
        return ConsumedEnergyByProsumer.findAll(client);
    }

    @GET
    @Path("/consumed-by-prosumer/{prosumerId}")
    public Multi<ConsumedEnergyByProsumer> getConsumedByProsumerId(@PathParam("prosumerId") Long prosumerId) {
        return ConsumedEnergyByProsumer.findByProsumerId(client, prosumerId);
    }

    @GET
    @Path("/average-soc")
    public Multi<AverageSoC> getAverageSoC() {
        return AverageSoC.findAll(client);
    }
}
