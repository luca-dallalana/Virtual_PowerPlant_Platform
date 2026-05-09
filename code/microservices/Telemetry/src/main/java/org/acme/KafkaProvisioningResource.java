package org.acme;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

import org.acme.model.Topic;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

@Path("Telemetry")
public class KafkaProvisioningResource {

        @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;
    
    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true") 
    boolean schemaCreate ;

    @ConfigProperty(name = "kafka.bootstrap.servers") 
    String kafka_servers;
    
    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }
    
    private void initdb() {
        // In a production environment this configuration SHOULD NOT be used
        client.query("DROP TABLE IF EXISTS Telemetry").execute()
        .flatMap(r -> client.query("CREATE TABLE Telemetry (id SERIAL PRIMARY KEY,   "                             
                                                            + " timeStamp DATETIME, " 
                                                            + " asset_id BIGINT UNSIGNED, " 
                                                            + " asset_type TEXT NOT NULL,  " 
                                                            + " grid_cell_id TEXT NOT NULL, "  
                                                            + " State_of_Charge	FLOAT, "  
                                                            + " Available_Energy FLOAT, "   
                                                            + " Current_Output	FLOAT, "  
                                                            + " Max_Capacity	FLOAT, "  
                                                            + " State_of_Health	FLOAT, "  
                                                            + " Status TEXT NOT NULL, " 
                                                            + " Current_Generation FLOAT, " 
                                                            + " Daily_Total FLOAT, " 
                                                            + " Grid_Voltage FLOAT, " 
                                                            + " Frequency FLOAT, " 
                                                            + " Plug_Status TEXT NOT NULL, " 
                                                            + " Charging_Rate FLOAT, " 
                                                            + " Session_Energy FLOAT, " 
                                                            + " EV_SoC FLOAT)").execute())
        .await().indefinitely();
    }

    @POST
    @Path("Consume")
    public String ProvisioningConsumer(Topic topic) {
        Thread worker = new DynamicTopicConsumer(topic.TopicName , kafka_servers , client);
        worker.start();
        return "New worker started";
    }

    @GET
    public Multi<Telemetry> get() {
        return Telemetry.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return Telemetry.findById(client, id)
                .onItem().transform(telemetry -> telemetry != null ? Response.ok(telemetry) : Response.status(Response.Status.NOT_FOUND)) 
                .onItem().transform(ResponseBuilder::build); 
    }

}
