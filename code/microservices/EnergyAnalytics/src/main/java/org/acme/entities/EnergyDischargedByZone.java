package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class EnergyDischargedByZone {

    public Long id;
    public String gridCellId;
    public Double totalEnergyDischargedKwh;
    public Integer batteryCount;
    public LocalDateTime timestamp;
    public String aggregationPeriod;

    public EnergyDischargedByZone() {
    }

    public EnergyDischargedByZone(Long id, String gridCellId, Double totalEnergyDischargedKwh,
                                 Integer batteryCount, LocalDateTime timestamp, String aggregationPeriod) {
        this.id = id;
        this.gridCellId = gridCellId;
        this.totalEnergyDischargedKwh = totalEnergyDischargedKwh;
        this.batteryCount = batteryCount;
        this.timestamp = timestamp;
        this.aggregationPeriod = aggregationPeriod;
    }

    private static EnergyDischargedByZone from(Row row) {
        return new EnergyDischargedByZone(
            row.getLong("id"),
            row.getString("gridCellId"),
            row.getDouble("totalEnergyDischargedKwh"),
            row.getInteger("batteryCount"),
            row.getLocalDateTime("timestamp"),
            row.getString("aggregationPeriod")
        );
    }

    public static Multi<EnergyDischargedByZone> findAll(MySQLPool client) {
        return client.query("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone ORDER BY timestamp DESC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(EnergyDischargedByZone::from);
    }

    public static Multi<EnergyDischargedByZone> findByGridCellId(MySQLPool client, String gridCellId) {
        return client.preparedQuery("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE gridCellId = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(gridCellId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(EnergyDischargedByZone::from);
    }

    public static Multi<EnergyDischargedByZone> findByPeriod(MySQLPool client, String aggregationPeriod) {
        return client.preparedQuery("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE aggregationPeriod = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(aggregationPeriod))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(EnergyDischargedByZone::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO EnergyDischargedByZone(gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)")
                .execute(Tuple.of(gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod))
                .onItem().transform(pgRowSet -> {
                    this.id = (Long) pgRowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID);
                    return this.id;
                });
    }
}
