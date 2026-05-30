package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class Telemetry 
{

    public Long id;
    public java.time.LocalDateTime timeStamp;
    public Long asset_id;
    public String asset_type;
    public String grid_cell_id;
    public Float State_of_Charge;
    public Float Available_Energy;
    public Float Current_Output;
    public Float Max_Capacity;
    public Float State_of_Health;
    public String Status;
    public Float Current_Generation;
    public Float Daily_Total;
    public Float Grid_Voltage;
    public Float Frequency;
    public String Plug_Status;
    public Float Charging_Rate;
    public Float Session_Energy;
    public Float EV_SoC;

    

    public Telemetry(Long id, LocalDateTime timeStamp, Long asset_id, String asset_type, String grid_cell_id,
            Float state_of_Charge, Float available_Energy, Float current_Output, Float max_Capacity,
            Float state_of_Health, String status, Float current_Generation, Float daily_Total, Float grid_Voltage,
            Float frequency, String plug_Status, Float charging_Rate, Float session_Energy, Float eV_SoC) {
        this.id = id;
        this.timeStamp = timeStamp;
        this.asset_id = asset_id;
        this.asset_type = asset_type;
        this.grid_cell_id = grid_cell_id;
        State_of_Charge = state_of_Charge;
        Available_Energy = available_Energy;
        Current_Output = current_Output;
        Max_Capacity = max_Capacity;
        State_of_Health = state_of_Health;
        Status = status;
        Current_Generation = current_Generation;
        Daily_Total = daily_Total;
        Grid_Voltage = grid_Voltage;
        Frequency = frequency;
        Plug_Status = plug_Status;
        Charging_Rate = charging_Rate;
        Session_Energy = session_Energy;
        EV_SoC = eV_SoC;
    }

    public Telemetry() {
    }

    @Override
    public String toString() {
        return "Telemetry [id=" + id + ", timeStamp=" + timeStamp + ", asset_id=" + asset_id + ", asset_type="
                + asset_type + ", grid_cell_id=" + grid_cell_id + ", State_of_Charge=" + State_of_Charge
                + ", Available_Energy=" + Available_Energy + ", Current_Output=" + Current_Output + ", Max_Capacity="
                + Max_Capacity + ", State_of_Health=" + State_of_Health + ", Status=" + Status + ", Current_Generation="
                + Current_Generation + ", Daily_Total=" + Daily_Total + ", Grid_Voltage=" + Grid_Voltage
                + ", Frequency=" + Frequency + ", Plug_Status=" + Plug_Status + ", Charging_Rate=" + Charging_Rate
                + ", Session_Energy=" + Session_Energy + ", EV_SoC=" + EV_SoC + "]";
    }
    

    private static Telemetry from(Row row) {
        return new Telemetry(
                            row.getLong("id"), 
                            row.getLocalDateTime("timeStamp"),
                            row.getLong("asset_id"),
                            row.getString("asset_type"),
                            row.getString("grid_cell_id"),
                            row.getFloat("State_of_Charge"),
                            row.getFloat("Available_Energy"),
                            row.getFloat("Current_Output"),
                            row.getFloat("Max_Capacity"),
                            row.getFloat("State_of_Health"),
                            row.getString("Status"),
                            row.getFloat("Current_Generation"),
                            row.getFloat("Daily_Total"),
                            row.getFloat("Grid_Voltage"),
                            row.getFloat("Frequency"),
                            row.getString("Plug_Status"),
                            row.getFloat("Charging_Rate"),
                            row.getFloat("Session_Energy"),
                            row.getFloat("EV_SoC")
                        );
    }
    
    public static Multi<Telemetry> findAll(MySQLPool client) {
        return client.query("SELECT *  FROM Telemetry ORDER BY id ASC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }
    
    public static Uni<Telemetry> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT * FROM Telemetry WHERE id = ?").execute(Tuple.of(id)) 
                .onItem().transform(RowSet::iterator) 
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null); 
    }

    public static Multi<Telemetry> findByTimeWindow(MySQLPool client, LocalDateTime from, LocalDateTime to) {
        return client.preparedQuery("SELECT * FROM Telemetry WHERE timeStamp >= ? AND timeStamp <= ? ORDER BY timeStamp ASC")
                .execute(Tuple.of(from, to))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(Telemetry::from);
    }

    public static Uni<Telemetry> findLatestByAssetId(MySQLPool client, Long assetId) {
        return client.preparedQuery("SELECT * FROM Telemetry WHERE asset_id = ? AND timeStamp >= NOW() - INTERVAL 10 MINUTE ORDER BY timeStamp DESC LIMIT 1")
                .execute(Tuple.of(assetId))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

}
