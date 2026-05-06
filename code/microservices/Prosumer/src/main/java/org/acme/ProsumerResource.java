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
        .flatMap(r -> client.query("CREATE TABLE Asset (assetId BIGINT PRIMARY KEY, prosumerId BIGINT NOT NULL, assetType TEXT NOT NULL, model TEXT NOT NULL, status TEXT NOT NULL, FOREIGN KEY (prosumerId) REFERENCES Prosumer(id) ON DELETE CASCADE)").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1001, 1, 'BATTERY', 'Tesla Powerwall 2', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1002, 1, 'SOLAR', 'SolarEdge SE7600H', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1003, 2, 'EV_CHARGER', 'ChargePoint Home Flex', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO Asset (assetId, prosumerId, assetType, model, status) VALUES (1004, 3, 'BATTERY', 'LG Chem RESU10H', 'MAINTENANCE')").execute())
        .await().indefinitely();
    }
    
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

    @GET
    @Path("{prosumerId}/assets")
    public Multi<Asset> getAssets(Long prosumerId) {
        return Asset.findByProsumerId(client, prosumerId);
    }

    @POST
    @Path("{prosumerId}/assets")
    public Uni<Response> createAsset(Long prosumerId, Asset asset) {
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

}
