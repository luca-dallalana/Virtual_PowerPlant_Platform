package org.acme;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.MediaType;

@Path("Prosumer")
public class ProsumerResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;
    
    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true") 
    boolean schemaCreate ;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }
    
    private void initdb() {
        // In a production environment this configuration SHOULD NOT be used
        client.query("DROP TABLE IF EXISTS Asset").execute()
        .flatMap(r -> client.query("DROP TABLE IF EXISTS Prosumer").execute())
        .flatMap(r -> client.query("CREATE TABLE Prosumer (id SERIAL PRIMARY KEY, name TEXT NOT NULL, FiscalNumber BIGINT UNSIGNED, location TEXT NOT NULL)").execute())
        .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client1','123456','Lisbon')").execute())
        .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client2','987654','Setúbal')").execute())
        .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client3','123987','OPorto')").execute())
        .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client4','987123','Faro')").execute())
        .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client5','456789','Faro')").execute())
        .flatMap(r -> client.query("INSERT INTO Prosumer (name,FiscalNumber,location) VALUES ('client6','654321','Faro')").execute())
        .flatMap(r -> client.query("CREATE TABLE Asset (assetId BIGINT PRIMARY KEY, prosumerId BIGINT UNSIGNED NOT NULL, assetType TEXT NOT NULL, model TEXT NOT NULL, status TEXT NOT NULL, FOREIGN KEY (prosumerId) REFERENCES Prosumer(id) ON DELETE CASCADE)").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1001, 1, 'BATTERY', 'Tesla Powerwall 2', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1002, 1, 'SOLAR', 'SolarEdge SE7600H', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1003, 2, 'EV_CHARGER', 'ChargePoint Home Flex', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1004, 3, 'BATTERY', 'LG Chem RESU10H', 'MAINTENANCE')").execute())
        // ── Prosumer 3 (OPorto) — PORTO-IN assets (overload scenario) ─────────────
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1005, 3, 'SOLAR', 'SolarEdge SE10000H', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1006, 3, 'EV_CHARGER', 'ABB Terra 184 DC', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1007, 3, 'EV_CHARGER', 'Mennekes AMTRON 22', 'ACTIVE')").execute())
        // ── Prosumer 4 (Faro) — FARO-RS battery + SETUBAL-CT solar & charger ──────
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1008, 4, 'BATTERY', 'Pylontech US5000C', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1009, 4, 'SOLAR', 'Fronius Symo 3.7-3-S', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1010, 4, 'EV_CHARGER', 'Wallbox Pulsar Plus', 'ACTIVE')").execute())
        // ── Prosumer 2 (Setúbal) — SETUBAL-CT battery (high SoC → ARBITRAGE_SELL) ─
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1011, 2, 'BATTERY', 'VARTA Element Backup 13', 'ACTIVE')").execute())
        // ── Prosumer 5 (Faro) — FARO-RS heavy load (50 kW DC charger → overload scenario) ──
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1012, 5, 'EV_CHARGER', 'ABB Terra 54 CJ', 'ACTIVE')").execute())
        // ── Prosumer 6 (Faro) — FARO-DW surplus (solar + battery → neighbour relief) ──────
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1013, 6, 'SOLAR', 'SMA Sunny Tripower 10', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1014, 6, 'BATTERY', 'BYD Battery-Box Premium HV', 'ACTIVE')").execute())
        .await().indefinitely();
    }
    
    // Prosumer-related endpoints

    @GET
    public Multi<Prosumer> get() {
        return Prosumer.findAll(client);
    }
    
    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return Prosumer.findById(client, id)
                .onItem().transform(prosumer -> prosumer != null ? Response.ok(prosumer) : Response.status(Response.Status.NOT_FOUND)) 
                .onItem().transform(ResponseBuilder::build); 
    }
     
    @POST
    public Uni<Response> create(Prosumer prosumer) {
        return prosumer.save(client , prosumer.name , prosumer.FiscalNumber , prosumer.location)
                .onItem().transform(id -> URI.create("/Prosumer/" + id))
                .onItem().transform(uri -> Response.created(uri).build());
    }
    
    @DELETE
    @Path("{id}")
    public Uni<Response> delete(Long id) {
        return Prosumer.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("/{id}/{name}/{FiscalNumber}/{location}")
    public Uni<Response> update(Long id , String name , Long FiscalNumber , String location) {
        return Prosumer.update(client, id , name , FiscalNumber , location)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    // Asset-related endpoints

    @GET
    @Path("{prosumerId}/assets")
    public Multi<Asset> getAssets(Long prosumerId) {
        return Asset.findByProsumerId(client, prosumerId);
    }

    @POST
    @Path("{prosumerId}/assets")
    public Uni<Response> createAsset(Long prosumerId, Asset asset) {
        if (asset.assetId == null) {
            asset.assetId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        }
        return asset.save(client, asset.assetId, prosumerId, asset.assetType, asset.model, asset.status)
                .onItem().transform(id -> URI.create("/Prosumer/" + prosumerId + "/assets/" + asset.assetId))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @DELETE
    @Path("{prosumerId}/assets/{assetId}")
    public Uni<Response> deleteAsset(Long prosumerId, Long assetId) {
        return Asset.delete(client, assetId)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{prosumerId}/assets/{assetId}/status/{status}")
    public Uni<Response> updateAssetStatus(Long prosumerId, Long assetId, String status) {
        return Asset.updateStatus(client, assetId, status)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status1 -> Response.status(status1).build());
    }

    @GET
    @Path("assets")
    public Multi<Asset> getAllAssets() {
        return Asset.findAll(client);
    }

    @GET
    @Path("assets/active")
    public Multi<AssetDTO> getAllActiveAssets() {
        return Asset.findAllActive(client);
    }

    @GET
    @Path("assets/active/{assetType}")
    public Multi<AssetDTO> getActiveAssetsByType(@PathParam("assetType") String assetType) {
        return Asset.findActiveByType(client, assetType);
    }

    @POST
    @Path("assets/active/by-prosumers")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Long> getActiveAssetIdsByProsumers(List<Long> prosumerIds) {
        return Asset.findActiveAssetIdsByProsumerIds(client, prosumerIds);
    }

}
