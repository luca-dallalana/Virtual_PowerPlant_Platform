package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class GeneratedEnergyByProsumer {

    public Long id;
    public Long prosumerId;
    public Double totalEnergyGeneratedKw;
    public Integer solarAssetCount;
    public LocalDateTime timestamp;
    public String aggregationPeriod;

    public GeneratedEnergyByProsumer() {
    }

    public GeneratedEnergyByProsumer(Long id, Long prosumerId, Double totalEnergyGeneratedKw,
                                    Integer solarAssetCount, LocalDateTime timestamp, String aggregationPeriod) {
        this.id = id;
        this.prosumerId = prosumerId;
        this.totalEnergyGeneratedKw = totalEnergyGeneratedKw;
        this.solarAssetCount = solarAssetCount;
        this.timestamp = timestamp;
        this.aggregationPeriod = aggregationPeriod;
    }

    private static GeneratedEnergyByProsumer from(Row row) {
        return new GeneratedEnergyByProsumer(
            row.getLong("id"),
            row.getLong("prosumerId"),
            row.getDouble("totalEnergyGeneratedKw"),
            row.getInteger("solarAssetCount"),
            row.getLocalDateTime("timestamp"),
            row.getString("aggregationPeriod")
        );
    }

    public static Multi<GeneratedEnergyByProsumer> findAll(MySQLPool client) {
        return client.query("SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer ORDER BY timestamp DESC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(GeneratedEnergyByProsumer::from);
    }

    public static Multi<GeneratedEnergyByProsumer> findByProsumerId(MySQLPool client, Long prosumerId) {
        return client.preparedQuery("SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(prosumerId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(GeneratedEnergyByProsumer::from);
    }

    public static Multi<GeneratedEnergyByProsumer> findByPeriod(MySQLPool client, String aggregationPeriod) {
        return client.preparedQuery("SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE aggregationPeriod = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(aggregationPeriod))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(GeneratedEnergyByProsumer::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO GeneratedEnergyByProsumer(prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)")
                .execute(Tuple.of(prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod))
                .onItem().transform(pgRowSet -> (Long) pgRowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }
}
