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
        client.query("DROP TABLE IF EXISTS GridCell").execute()
        .flatMap(r -> client.query("DROP TABLE IF EXISTS UtilityOperator").execute())
        .flatMap(r -> client.query("CREATE TABLE UtilityOperator (id SERIAL PRIMARY KEY, name TEXT NOT NULL, location TEXT NOT NULL)").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('ArcoCegoLisbon','Lisboa')").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('PracadeBocage','Setubal')").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('PracadaBoavista','Porto')").execute())
        .flatMap(r -> client.query("INSERT INTO UtilityOperator (name,location) VALUES ('PracaDomFranciscoGomes','Faro')").execute())
        .flatMap(r -> client.query("CREATE TABLE GridCell (gridCellId VARCHAR(100) PRIMARY KEY, utilityOperatorId BIGINT UNSIGNED NOT NULL, maxCapacity DOUBLE NOT NULL, geographicBoundaries TEXT NOT NULL, FOREIGN KEY (utilityOperatorId) REFERENCES UtilityOperator(id))").execute())
        .flatMap(r -> client.query("INSERT INTO GridCell (gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES ('LISBON-DT', 1, 50.0, 'Lisbon Downtown Area')").execute())
        .flatMap(r -> client.query("INSERT INTO GridCell (gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES ('PORTO-IN', 3, 75.0, 'Porto Industrial Zone')").execute())
        .flatMap(r -> client.query("INSERT INTO GridCell (gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES ('SETUBAL-CT', 2, 40.0, 'Setubal Central')").execute())
        .flatMap(r -> client.query("INSERT INTO GridCell (gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES ('FARO-RS', 4, 30.0, 'Faro Residential')").execute())
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

    @GET
    @Path("gridcell")
    public Multi<GridCell> getAllGridCells() {
        return GridCell.findAll(client);
    }

    @GET
    @Path("gridcell/{gridCellId}")
    public Uni<Response> getGridCell(String gridCellId) {
        return GridCell.findByGridCellId(client, gridCellId)
                .onItem().transform(gridCell -> gridCell != null ? Response.ok(gridCell) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("{utilityOperatorId}/gridcells")
    public Multi<GridCell> getGridCellsByOperator(Long utilityOperatorId) {
        return GridCell.findByUtilityOperatorId(client, utilityOperatorId);
    }

    @POST
    @Path("gridcell")
    public Uni<Response> createGridCell(GridCell gridCell) {
        return gridCell.save(client, gridCell.gridCellId, gridCell.utilityOperatorId, gridCell.maxCapacity, gridCell.geographicBoundaries)
                .onItem().transform(id -> URI.create("/UtilityOperator/gridcell/" + gridCell.gridCellId))
                .onItem().transform(uri -> Response.created(uri).build());
    }

    @DELETE
    @Path("gridcell/{gridCellId}")
    public Uni<Response> deleteGridCell(String gridCellId) {
        return GridCell.delete(client, gridCellId)
                .onItem().transform(deleted -> deleted ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("gridcell/{gridCellId}")
    public Uni<Response> updateGridCell(String gridCellId, GridCell gridCell) {
        return GridCell.update(client, gridCellId, gridCell.utilityOperatorId, gridCell.maxCapacity, gridCell.geographicBoundaries)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

    @PUT
    @Path("gridcell/{gridCellId}/capacity/{maxCapacity}")
    public Uni<Response> updateGridCellCapacity(String gridCellId, Double maxCapacity) {
        return GridCell.updateMaxCapacity(client, gridCellId, maxCapacity)
                .onItem().transform(updated -> updated ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND)
                .onItem().transform(status -> Response.status(status).build());
    }

}
