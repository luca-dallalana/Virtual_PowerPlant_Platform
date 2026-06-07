package org.acme;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import java.time.LocalDateTime;
import java.util.List;

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
        // Column shorthand used in every INSERT below:
        // timeStamp, asset_id, asset_type, grid_cell_id,
        // State_of_Charge, Available_Energy, Current_Output, Max_Capacity, State_of_Health, Status,
        // Current_Generation, Daily_Total, Grid_Voltage, Frequency,
        // Plug_Status, Charging_Rate, Session_Energy, EV_SoC
        //
        // BATTERY  fields: SoC, Available_Energy, Current_Output (+ve=discharge/supply, -ve=charge/demand),
        //                  Max_Capacity (max discharge kW), SoH, Status ('ONLINE'/'MAINTENANCE')
        // SOLAR    fields: Current_Generation (kW), Daily_Total (kWh), Grid_Voltage (V), Frequency (Hz)
        // EV_CHARGER fields: Plug_Status, Charging_Rate (kW), Session_Energy (kWh), EV_SoC (%)
        //
        // NOT NULL TEXT columns (Status, Plug_Status) use '' when not applicable for the asset type.
        // Timestamps use NOW()-INTERVAL X MINUTE so readings are always fresh at startup.
        // All 5 readings per asset fall within the 10-min FlexibilityEmission window and the
        // 30-min EnergyAnalytics window. GridBalancing uses findLatestByAssetIds (no age limit).
        //
        // Asset 1001 (Tesla Powerwall 2 / BATTERY / ACTIVE / LISBON-DT):
        //   Discharging at 5.5 kW, SoC drops ~1.4 % per 2-min interval (5.5 kW × 2/60 h / 13.5 kWh).
        //   Latest reading: SoC = 90.9 % (> 90 %) → FlexibilityEvent emits ARBITRAGE_SELL.
        //
        // Asset 1002 (SolarEdge SE7600H / SOLAR / ACTIVE / LISBON-DT):
        //   Mid-morning ramp-up 4.2 → 5.2 kW. Daily_Total increments match generation rate.
        //
        // Asset 1003 (ChargePoint Home Flex / EV_CHARGER / ACTIVE / LISBON-DT):
        //   Stable 7.5 kW session. Prosumer 2 is enrolled with UtilityOperator 1 (Lisbon), so
        //   grid_cell_id = 'LISBON-DT' lets GridBalancing count this charger under LISBON-DT.
        //
        // Asset 1004 (LG Chem RESU10H / BATTERY / MAINTENANCE / PORTO-IN):
        //   Trickle-charging at -2 kW, SoC rising ~0.4 % per interval (2 kW × 2/60 h / 9.8 kWh).
        //   MAINTENANCE status → excluded from FlexibilityEvent (BPMN only fetches ACTIVE assets).
        //   Included in EnergyAnalytics average-SoC across the fleet.
        //
        // GridBalancing result for LISBON-DT (maxCapacity 50 kW, threshold 90 % = 45 kW):
        //   supply = 5.5 (battery discharge) + 5.2 (solar) = 10.7 kW
        //   demand = 7.5 (EV charger)
        //   net load = max(0, 7.5 - 10.7) = 0 kW → below threshold → no recommendation.
        //   To trigger a recommendation in testing, lower LISBON-DT maxCapacity to ~9 kW via
        //   PUT /UtilityOperator/gridcells/LISBON-DT/capacity/9.0 before running the BPMN.

        final String INS = "INSERT INTO Telemetry (timeStamp,asset_id,asset_type,grid_cell_id,"
            + "State_of_Charge,Available_Energy,Current_Output,Max_Capacity,State_of_Health,Status,"
            + "Current_Generation,Daily_Total,Grid_Voltage,Frequency,"
            + "Plug_Status,Charging_Rate,Session_Energy,EV_SoC) VALUES ";

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
        // ── Asset 1001: Tesla Powerwall 2  (BATTERY / ACTIVE / LISBON-DT) ─────────────────────
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1001,'BATTERY','LISBON-DT', 96.3,13.00, 5.5,7.0,97.5,'ONLINE',   NULL, NULL,  NULL,  NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1001,'BATTERY','LISBON-DT', 94.9,12.81, 5.5,7.0,97.5,'ONLINE',   NULL, NULL,  NULL,  NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1001,'BATTERY','LISBON-DT', 93.6,12.64, 5.5,7.0,97.5,'ONLINE',   NULL, NULL,  NULL,  NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1001,'BATTERY','LISBON-DT', 92.3,12.46, 5.5,7.0,97.5,'ONLINE',   NULL, NULL,  NULL,  NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1001,'BATTERY','LISBON-DT', 90.9,12.27, 5.5,7.0,97.5,'ONLINE',   NULL, NULL,  NULL,  NULL,'',   NULL,NULL,NULL)").execute())
        // ── Asset 1002: SolarEdge SE7600H  (SOLAR / ACTIVE / LISBON-DT) ──────────────────────
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1002,'SOLAR','LISBON-DT',   NULL, NULL,NULL,NULL, NULL,'',        4.2,  8.50,231.2, 50.01,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1002,'SOLAR','LISBON-DT',   NULL, NULL,NULL,NULL, NULL,'',        4.5,  8.64,230.8, 49.98,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1002,'SOLAR','LISBON-DT',   NULL, NULL,NULL,NULL, NULL,'',        4.8,  8.79,230.5, 50.02,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1002,'SOLAR','LISBON-DT',   NULL, NULL,NULL,NULL, NULL,'',        5.0,  8.95,230.3, 50.00,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1002,'SOLAR','LISBON-DT',   NULL, NULL,NULL,NULL, NULL,'',        5.2,  9.12,230.1, 49.99,'',   NULL,NULL,NULL)").execute())
        // ── Asset 1003: ChargePoint Home Flex  (EV_CHARGER / ACTIVE / LISBON-DT) ─────────────
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1003,'EV_CHARGER','LISBON-DT',NULL,NULL,NULL,NULL,NULL,'', NULL,NULL,NULL,NULL,'CHARGING',7.5, 0.50,28.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1003,'EV_CHARGER','LISBON-DT',NULL,NULL,NULL,NULL,NULL,'', NULL,NULL,NULL,NULL,'CHARGING',7.5, 0.75,29.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1003,'EV_CHARGER','LISBON-DT',NULL,NULL,NULL,NULL,NULL,'', NULL,NULL,NULL,NULL,'CHARGING',7.5, 1.00,30.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1003,'EV_CHARGER','LISBON-DT',NULL,NULL,NULL,NULL,NULL,'', NULL,NULL,NULL,NULL,'CHARGING',7.5, 1.25,31.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1003,'EV_CHARGER','LISBON-DT',NULL,NULL,NULL,NULL,NULL,'', NULL,NULL,NULL,NULL,'CHARGING',7.5, 1.50,32.0)").execute())
        // ── Asset 1004: LG Chem RESU10H  (BATTERY / MAINTENANCE / PORTO-IN) ──────────────────
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1004,'BATTERY','PORTO-IN',  44.5, 4.36,-2.0,5.0,91.2,'MAINTENANCE',NULL,NULL,NULL,NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1004,'BATTERY','PORTO-IN',  44.9, 4.40,-2.0,5.0,91.2,'MAINTENANCE',NULL,NULL,NULL,NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1004,'BATTERY','PORTO-IN',  45.4, 4.45,-2.0,5.0,91.2,'MAINTENANCE',NULL,NULL,NULL,NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1004,'BATTERY','PORTO-IN',  45.8, 4.49,-2.0,5.0,91.2,'MAINTENANCE',NULL,NULL,NULL,NULL,'',   NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1004,'BATTERY','PORTO-IN',  46.2, 4.53,-2.0,5.0,91.2,'MAINTENANCE',NULL,NULL,NULL,NULL,'',   NULL,NULL,NULL)").execute())
        // ── Asset 1005: SolarEdge SE10000H  (SOLAR / ACTIVE / PORTO-IN) ─────────────────────────
        // Morning ramp-up 5.8 → 7.5 kW. Daily_Total increments match generation × 2-min interval.
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1005,'SOLAR','PORTO-IN',    NULL,NULL,NULL,NULL,NULL,'',  5.8,12.45,230.6,50.01,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1005,'SOLAR','PORTO-IN',    NULL,NULL,NULL,NULL,NULL,'',  6.2,12.64,230.4,50.00,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1005,'SOLAR','PORTO-IN',    NULL,NULL,NULL,NULL,NULL,'',  6.7,12.85,230.2,49.98,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1005,'SOLAR','PORTO-IN',    NULL,NULL,NULL,NULL,NULL,'',  7.1,13.07,230.0,50.02,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1005,'SOLAR','PORTO-IN',    NULL,NULL,NULL,NULL,NULL,'',  7.5,13.31,229.8,50.00,'',NULL,NULL,NULL)").execute())
        // ── Asset 1006: ABB Terra 184 DC  (EV_CHARGER / ACTIVE / PORTO-IN) ──────────────────────
        // 70 kW DC fast charger. Session_Energy: 70×2/60=2.33 kWh/interval. EV ~100 kWh battery.
        // PORTO-IN overload: supply=7.5 kW (1005), demand=70+22=92 kW (1006+1007),
        //   net=84.5 kW > threshold 67.5 kW (75×0.9) → GridBalancing recommends shift to LISBON-DT.
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1006,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',70.0, 8.17,42.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1006,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',70.0,10.50,44.3)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1006,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',70.0,12.83,46.6)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1006,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',70.0,15.17,48.9)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1006,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',70.0,17.50,51.2)").execute())
        // ── Asset 1007: Mennekes AMTRON 22  (EV_CHARGER / ACTIVE / PORTO-IN) ────────────────────
        // 22 kW AC home charger. Session_Energy: 22×2/60=0.73 kWh/interval. EV ~60 kWh battery.
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1007,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',22.0, 2.20,28.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1007,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',22.0, 2.93,29.2)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1007,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',22.0, 3.67,30.5)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1007,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',22.0, 4.40,31.7)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1007,'EV_CHARGER','PORTO-IN',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',22.0, 5.13,32.9)").execute())
        // ── Asset 1008: Pylontech US5000C  (BATTERY / ACTIVE / FARO-RS) ─────────────────────────
        // Depleted battery trickle-charging at -1.5 kW. SoC rising ~0.52%/2min (1.5×2/60/9.6×100).
        // SoC = 15.3–17.3% (all < 20%) → FlexibilityEvent emits BALANCING_UNAVAILABLE.
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1008,'BATTERY','FARO-RS',   15.3,1.47,-1.5,5.0,99.0,'ONLINE',     NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1008,'BATTERY','FARO-RS',   15.8,1.52,-1.5,5.0,99.0,'ONLINE',     NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1008,'BATTERY','FARO-RS',   16.3,1.57,-1.5,5.0,99.0,'ONLINE',     NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1008,'BATTERY','FARO-RS',   16.8,1.61,-1.5,5.0,99.0,'ONLINE',     NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1008,'BATTERY','FARO-RS',   17.3,1.66,-1.5,5.0,99.0,'ONLINE',     NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        // ── Asset 1009: Fronius Symo 3.7-3-S  (SOLAR / ACTIVE / SETUBAL-CT) ────────────────────
        // 3.7 kW rooftop solar for Prosumer 4. Stable noon generation.
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1009,'SOLAR','SETUBAL-CT',  NULL,NULL,NULL,NULL,NULL,'',  3.1, 9.20,231.0,50.01,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1009,'SOLAR','SETUBAL-CT',  NULL,NULL,NULL,NULL,NULL,'',  3.2, 9.30,230.8,50.00,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1009,'SOLAR','SETUBAL-CT',  NULL,NULL,NULL,NULL,NULL,'',  3.2, 9.41,230.7,49.99,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1009,'SOLAR','SETUBAL-CT',  NULL,NULL,NULL,NULL,NULL,'',  3.3, 9.52,230.5,50.00,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1009,'SOLAR','SETUBAL-CT',  NULL,NULL,NULL,NULL,NULL,'',  3.4, 9.63,230.3,50.01,'',NULL,NULL,NULL)").execute())
        // ── Asset 1010: Wallbox Pulsar Plus  (EV_CHARGER / ACTIVE / SETUBAL-CT) ──────────────────
        // 11 kW home charger. Session_Energy: 11×2/60=0.37 kWh/interval. EV ~50 kWh battery.
        // SETUBAL-CT net load = max(0, 11.0-3.4) = 7.6 kW < threshold 36 kW → no overload (baseline).
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1010,'EV_CHARGER','SETUBAL-CT',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',11.0,1.10,61.0)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1010,'EV_CHARGER','SETUBAL-CT',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',11.0,1.47,61.7)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1010,'EV_CHARGER','SETUBAL-CT',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',11.0,1.83,62.5)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1010,'EV_CHARGER','SETUBAL-CT',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',11.0,2.20,63.2)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1010,'EV_CHARGER','SETUBAL-CT',NULL,NULL,NULL,NULL,NULL,'',NULL,NULL,NULL,NULL,'CHARGING',11.0,2.57,64.0)").execute())
        // ── Asset 1011: VARTA Element Backup 13  (BATTERY / ACTIVE / SETUBAL-CT) ──────────────────
        // 13.0 kWh, max 3.3 kW discharge. Prosumer 2 (Setúbal). Discharging at 3.3 kW.
        // SoC drops ~0.85%/2min (3.3×2/60/13.0×100). SoC = 97.5–94.1% (all >90%) → ARBITRAGE_SELL.
        // Available_Energy = SoC% × 13.0 kWh.
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 8 MINUTE,1011,'BATTERY','SETUBAL-CT',  97.5,12.68, 3.3,3.3,98.5,'ONLINE',   NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 6 MINUTE,1011,'BATTERY','SETUBAL-CT',  96.6,12.56, 3.3,3.3,98.5,'ONLINE',   NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 4 MINUTE,1011,'BATTERY','SETUBAL-CT',  95.8,12.45, 3.3,3.3,98.5,'ONLINE',   NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW()-INTERVAL 2 MINUTE,1011,'BATTERY','SETUBAL-CT',  94.9,12.34, 3.3,3.3,98.5,'ONLINE',   NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
        .flatMap(r -> client.query(INS + "(NOW(),                  1011,'BATTERY','SETUBAL-CT',  94.1,12.23, 3.3,3.3,98.5,'ONLINE',   NULL,NULL,NULL,NULL,'',NULL,NULL,NULL)").execute())
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
    public Multi<Telemetry> get(@QueryParam("from") String from, @QueryParam("to") String to) {
        if (from != null && to != null) {
            return Telemetry.findByTimeWindow(client, LocalDateTime.parse(from), LocalDateTime.parse(to));
        }
        return Telemetry.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return Telemetry.findById(client, id)
                .onItem().transform(telemetry -> telemetry != null ? Response.ok(telemetry) : Response.status(Response.Status.NOT_FOUND)) 
                .onItem().transform(ResponseBuilder::build); 
    }

    @GET
    @Path("latest/{assetId}")
    public Uni<Response> getLatestByAssetId(Long assetId) {
        return Telemetry.findLatestByAssetId(client, assetId)
                .onItem().transform(telemetry -> telemetry != null ? Response.ok(telemetry) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("latest/{assetType}/{minutes}")
    public Multi<Telemetry> getLatestByAssetType(@PathParam("assetType") String assetType, @PathParam("minutes") int minutes) {
        return Telemetry.findLatestByAssetType(client, assetType, minutes);
    }

    @GET
    @Path("window/{assetType}/{minutes}")
    public Multi<Telemetry> getWindowByAssetType(@PathParam("assetType") String assetType, @PathParam("minutes") int minutes) {
        return Telemetry.findWindowByAssetType(client, assetType, minutes);
    }

    @POST
    @Path("latest/bulk")
    public Multi<Telemetry> getLatestByAssetIds(List<Long> assetIds,
            @QueryParam("maxAgeMinutes") Integer maxAgeMinutes) {
        return Telemetry.findLatestByAssetIds(client, assetIds, maxAgeMinutes);
    }

}
