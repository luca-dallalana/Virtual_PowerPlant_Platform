package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class ConsumedEnergyByProsumer {

    public Long id;
    public Long prosumerId;
    public Double totalEnergyConsumedKwh;
    public Integer evChargerCount;
    public LocalDateTime timestamp;
    public String aggregationPeriod;

    public ConsumedEnergyByProsumer() {
    }

    public ConsumedEnergyByProsumer(Long id, Long prosumerId, Double totalEnergyConsumedKwh,
                                   Integer evChargerCount, LocalDateTime timestamp, String aggregationPeriod) {
        this.id = id;
        this.prosumerId = prosumerId;
        this.totalEnergyConsumedKwh = totalEnergyConsumedKwh;
        this.evChargerCount = evChargerCount;
        this.timestamp = timestamp;
        this.aggregationPeriod = aggregationPeriod;
    }

    private static ConsumedEnergyByProsumer from(Row row) {
        return new ConsumedEnergyByProsumer(
            row.getLong("id"),
            row.getLong("prosumerId"),
            row.getDouble("totalEnergyConsumedKwh"),
            row.getInteger("evChargerCount"),
            row.getLocalDateTime("timestamp"),
            row.getString("aggregationPeriod")
        );
    }

    public static Multi<ConsumedEnergyByProsumer> findAll(MySQLPool client) {
        return client.query("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer ORDER BY timestamp DESC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(ConsumedEnergyByProsumer::from);
    }

    public static Multi<ConsumedEnergyByProsumer> findByProsumerId(MySQLPool client, Long prosumerId) {
        return client.preparedQuery("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(prosumerId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(ConsumedEnergyByProsumer::from);
    }

    public static Multi<ConsumedEnergyByProsumer> findByPeriod(MySQLPool client, String aggregationPeriod) {
        return client.preparedQuery("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE aggregationPeriod = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(aggregationPeriod))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(ConsumedEnergyByProsumer::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO ConsumedEnergyByProsumer(prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)")
                .execute(Tuple.of(prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod))
                .onItem().transform(pgRowSet -> (Long) pgRowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }
}
