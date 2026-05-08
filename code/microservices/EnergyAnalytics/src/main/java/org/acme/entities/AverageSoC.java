package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class AverageSoC {

    public Long id;
    public Double averageSocPercent;
    public Integer batteryCount;
    public LocalDateTime timestamp;
    public String aggregationPeriod;

    public AverageSoC() {
    }

    public AverageSoC(Long id, Double averageSocPercent, Integer batteryCount,
                     LocalDateTime timestamp, String aggregationPeriod) {
        this.id = id;
        this.averageSocPercent = averageSocPercent;
        this.batteryCount = batteryCount;
        this.timestamp = timestamp;
        this.aggregationPeriod = aggregationPeriod;
    }

    private static AverageSoC from(Row row) {
        return new AverageSoC(
            row.getLong("id"),
            row.getDouble("averageSocPercent"),
            row.getInteger("batteryCount"),
            row.getLocalDateTime("timestamp"),
            row.getString("aggregationPeriod")
        );
    }

    public static Multi<AverageSoC> findAll(MySQLPool client) {
        return client.query("SELECT id, averageSocPercent, batteryCount, timestamp, aggregationPeriod FROM AverageSoC ORDER BY timestamp DESC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AverageSoC::from);
    }

    public static Multi<AverageSoC> findByPeriod(MySQLPool client, String aggregationPeriod) {
        return client.preparedQuery("SELECT id, averageSocPercent, batteryCount, timestamp, aggregationPeriod FROM AverageSoC WHERE aggregationPeriod = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(aggregationPeriod))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(AverageSoC::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO AverageSoC(averageSocPercent, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?)")
                .execute(Tuple.of(averageSocPercent, batteryCount, timestamp, aggregationPeriod))
                .onItem().transform(pgRowSet -> (Long) pgRowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }
}
