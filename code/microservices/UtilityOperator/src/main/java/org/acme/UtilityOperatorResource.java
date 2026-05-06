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

@Path("UtilityOperator")
public class UtilityOperatorResource {

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
        client.query("DROP TABLE IF EXISTS UtilityOperator").execute()
        .flatMap(r -> client.query("CREATE TABLE UtilityOperator (id SERIAL PRIMARY KEY, name TEXT NOT NULL, location TEXT NOT NULL)").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('ArcoCegoLisbon','Lisboa')").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('PracadeBocage','Setubal')").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('PracadaBoavista','Porto')").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('PracaDomFranciscoGomes','Faro')").execute())
        .await().indefinitely();
    }
    
    @GET
    public Multi<UtilityOperator> get() {
        return UtilityOperator.findAll(client);
    }
    
    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return UtilityOperator.findById(client, id)
                .onItem().transform(operator -> operator != null ? Response.ok(operator) : Response.status(Response.Status.NOT_FOUND)) 
                .onItem().transform(ResponseBuilder::build); 
    }
     
    @POST
    public Uni<Response> create(UtilityOperator operator) {
        return operator.save(client , operator.name , operator.location)
                .onItem().transform(id -> URI.create("/UtilityOperator/" + id))
                .onItem().transform(uri -> Response.created(uri).build());
    }
    
    @DELETE
    @Path("{id}")
    public Uni<Response> delete(Long id) {
        return UtilityOperator.delete(client, id)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("/{id}/{name}/{location}")
    public Uni<Response> update(Long id , String name , String location) {
        return UtilityOperator.update(client, id , name , location)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }
    
}
