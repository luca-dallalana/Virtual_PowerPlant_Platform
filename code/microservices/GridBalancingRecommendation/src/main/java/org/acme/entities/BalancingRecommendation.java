package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class BalancingRecommendation {

    public Long id;
    public String sourceGridCellId;
    public String targetGridCellId;
    public Double sourceNetLoadKw;
    public Double targetHeadroomKw;
    public Double overloadKw;
    public Double transferableKw;
    public Double thresholdPercent;
    public String status;
    public String rationale;
    public LocalDateTime createdAt;

    public BalancingRecommendation() {
    }

    public BalancingRecommendation(Long id, String sourceGridCellId, String targetGridCellId,
                                   Double sourceNetLoadKw, Double targetHeadroomKw, Double overloadKw,
                                   Double transferableKw, Double thresholdPercent, String status,
                                   String rationale, LocalDateTime createdAt) {
        this.id = id;
        this.sourceGridCellId = sourceGridCellId;
        this.targetGridCellId = targetGridCellId;
        this.sourceNetLoadKw = sourceNetLoadKw;
        this.targetHeadroomKw = targetHeadroomKw;
        this.overloadKw = overloadKw;
        this.transferableKw = transferableKw;
        this.thresholdPercent = thresholdPercent;
        this.status = status;
        this.rationale = rationale;
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "{id:" + id + ", sourceGridCellId:" + sourceGridCellId + ", targetGridCellId:" + targetGridCellId
                + ", sourceNetLoadKw:" + sourceNetLoadKw + ", targetHeadroomKw:" + targetHeadroomKw
                + ", overloadKw:" + overloadKw + ", transferableKw:" + transferableKw
                + ", thresholdPercent:" + thresholdPercent + ", status:" + status
                + ", rationale:" + rationale + ", createdAt:" + createdAt + "}\n";
    }

    private static BalancingRecommendation from(Row row) {
        return new BalancingRecommendation(
                row.getLong("id"),
                row.getString("sourceGridCellId"),
                row.getString("targetGridCellId"),
                row.getDouble("sourceNetLoadKw"),
                row.getDouble("targetHeadroomKw"),
                row.getDouble("overloadKw"),
                row.getDouble("transferableKw"),
                row.getDouble("thresholdPercent"),
                row.getString("status"),
                row.getString("rationale"),
                row.getLocalDateTime("createdAt")
        );
    }

    public static Multi<BalancingRecommendation> findAll(MySQLPool client) {
        return client.query("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, "
                + "overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt "
                + "FROM BalancingRecommendation ORDER BY createdAt DESC")
                .execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(BalancingRecommendation::from);
    }

    public static Uni<BalancingRecommendation> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, "
                + "overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt "
                + "FROM BalancingRecommendation WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<BalancingRecommendation> findBySourceGridCellId(MySQLPool client, String sourceGridCellId) {
        return client.preparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, "
                + "overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt "
                + "FROM BalancingRecommendation WHERE sourceGridCellId = ? ORDER BY createdAt DESC")
                .execute(Tuple.of(sourceGridCellId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(BalancingRecommendation::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO BalancingRecommendation("
                + "sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, "
                + "transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)")
                .execute(Tuple.from(java.util.Arrays.asList(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw,
                        overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt)))
                .onItem().transform(pgRowSet -> (Long) pgRowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    public static Uni<Boolean> delete(MySQLPool client, Long id) {
        return client.preparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }

    public static Uni<Boolean> update(MySQLPool client, Long id, String sourceGridCellId, String targetGridCellId,
                                     Double sourceNetLoadKw, Double targetHeadroomKw, Double overloadKw,
                                     Double transferableKw, Double thresholdPercent, String status, String rationale,
                                     LocalDateTime createdAt) {
        return client.preparedQuery("UPDATE BalancingRecommendation SET sourceGridCellId = ?, targetGridCellId = ?, "
                + "sourceNetLoadKw = ?, targetHeadroomKw = ?, overloadKw = ?, transferableKw = ?, thresholdPercent = ?, "
                + "status = ?, rationale = ?, createdAt = ? WHERE id = ?")
                .execute(Tuple.from(java.util.Arrays.asList(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw,
                        overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt, id)))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}
