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

@Path("AssetLink")
public class AssetLinkResource {

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
        client.query("DROP TABLE IF EXISTS AssetLink").execute()
        .flatMap(r -> client.query("CREATE TABLE AssetLink (assetLinkId SERIAL PRIMARY KEY, assetId BIGINT UNSIGNED NOT NULL, prosumerId BIGINT UNSIGNED NOT NULL, utilityOperatorId BIGINT UNSIGNED NOT NULL, gridCellId VARCHAR(100) NOT NULL, status VARCHAR(50) NOT NULL, CONSTRAINT UC_Asset_Utility UNIQUE (assetId, utilityOperatorId))").execute())
        .flatMap(r -> client.query("INSERT INTO AssetLink (assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (1, 1, 1, 'LISBON-DT', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO AssetLink (assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (2, 1, 1, 'LISBON-DT', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO AssetLink (assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (3, 2, 1, 'PORTO-IN', 'ACTIVE')").execute())
        .flatMap(r -> client.query("INSERT INTO AssetLink (assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (4, 3, 2, 'COIMBRA-DT', 'ACTIVE')").execute())
        .await().indefinitely();
    }
    
    @GET
    public Multi<AssetLink> get() {
        return AssetLink.findAll(client);
    }

    @GET
    @Path("{assetLinkId}")
    public Uni<Response> getSingle(Long assetLinkId) {
        return AssetLink.findById(client, assetLinkId)
                .onItem().transform(assetlink -> assetlink != null ? Response.ok(assetlink) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("asset/{assetId}")
    public Multi<AssetLink> getByAssetId(Long assetId) {
        return AssetLink.findByAssetId(client, assetId);
    }

    @GET
    @Path("gridcell/{gridCellId}")
    public Multi<AssetLink> getByGridCellId(String gridCellId) {
        return AssetLink.findByGridCellId(client, gridCellId);
    }

    @GET
    @Path("status/{status}")
    public Multi<AssetLink> getByStatus(String status) {
        return AssetLink.findByStatus(client, status);
    }

    @POST
    public Uni<Response> create(AssetLink assetlink) {
        return assetlink.save(client, assetlink.assetId, assetlink.prosumerId, assetlink.utilityOperatorId, assetlink.gridCellId, assetlink.status)
                .onItem().transform(id -> URI.create("/AssetLink/" + assetlink.assetLinkId))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @DELETE
    @Path("{assetLinkId}")
    public Uni<Response> delete(Long assetLinkId) {
        return AssetLink.delete(client, assetLinkId)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{assetLinkId}")
    public Uni<Response> update(Long assetLinkId, AssetLink assetlink) {
        return AssetLink.update(client, assetLinkId, assetlink.assetId, assetlink.prosumerId, assetlink.utilityOperatorId, assetlink.gridCellId, assetlink.status)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{assetLinkId}/status/{status}")
    public Uni<Response> updateStatus(Long assetLinkId, String status) {
        return AssetLink.updateStatus(client, assetLinkId, status)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status2 -> Response.status(status2).build());
    }
    
}
