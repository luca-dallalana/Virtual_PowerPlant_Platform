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
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.acme.dto.AssetLinkRegistrationResponse;

@Path("AssetLink")
public class AssetLinkResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    @Inject
    @Channel("assetlink-events")
    Emitter<String> assetLinkEventsEmitter;

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
        return assetlink.save(client, assetlink.assetId, assetlink.prosumerId,
                             assetlink.utilityOperatorId, assetlink.gridCellId, assetlink.status)
                .onItem().invoke(success -> {
                    publishAssetLinkEvent(assetlink, "CREATED");
                })
                .onItem().transform(success -> {
                    return Response.status(Response.Status.CREATED)
                        .entity(assetlink)
                        .build();
                });
    }

    @DELETE
    @Path("{assetLinkId}")
    public Uni<Response> delete(Long assetLinkId) {
        return AssetLink.findById(client, assetLinkId)
                .flatMap(assetLink -> {
                    if (assetLink == null) {
                        return Uni.createFrom().item(false);
                    }
                    return AssetLink.delete(client, assetLinkId)
                            .onItem().invoke(deleted -> {
                                if (deleted) {
                                    publishAssetLinkEvent(assetLink, "DELETED");
                                }
                            });
                })
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{assetLinkId}")
    public Uni<Response> update(Long assetLinkId, AssetLink assetlink) {
        assetlink.assetLinkId = assetLinkId;
        return AssetLink.update(client, assetLinkId, assetlink.assetId, assetlink.prosumerId, assetlink.utilityOperatorId, assetlink.gridCellId, assetlink.status)
                .onItem().invoke(updated -> { if (updated) publishAssetLinkEvent(assetlink, "UPDATED"); })
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("{assetLinkId}/status/{status}")
    public Uni<Response> updateStatus(Long assetLinkId, String status) {
        return AssetLink.updateStatus(client, assetLinkId, status)
                .flatMap(updated -> {
                    if (updated) {
                        return AssetLink.findById(client, assetLinkId)
                            .onItem().invoke(assetLink -> publishAssetLinkEvent(assetLink, "UPDATED"))
                            .replaceWith(updated);
                    }
                    return Uni.createFrom().item(updated);
                })
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status2 -> Response.status(status2).build());
    }

    private void publishAssetLinkEvent(AssetLink assetLink, String eventType) {
        String json;
        String key;

        if ("DELETED".equals(eventType)) {
            json = String.format(
                "{\"assetLinkId\":%d,\"eventType\":\"%s\"}",
                assetLink.assetLinkId, eventType
            );
            key = assetLink.assetLinkId.toString();
        } else {
            json = String.format(
                "{\"assetLinkId\":%d,\"assetId\":%d,\"prosumerId\":%d,\"utilityOperatorId\":%d,\"gridCellId\":\"%s\",\"status\":\"%s\",\"eventType\":\"%s\"}",
                assetLink.assetLinkId, assetLink.assetId, assetLink.prosumerId,
                assetLink.utilityOperatorId, assetLink.gridCellId, assetLink.status, eventType
            );
            key = assetLink.assetId.toString();
        }

        assetLinkEventsEmitter.send(Message.of(json)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(key).build()));

        System.out.printf("Published AssetLink-%s event: assetLinkId=%d\n",
            eventType, assetLink.assetLinkId);
    }

}
