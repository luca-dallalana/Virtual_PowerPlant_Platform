package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLClient;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

import java.time.LocalDateTime;
import java.util.Arrays;

public class BalancingRecommendation {

    public Long id;
    public Long assetId;
    public String action;
    public String fromCell;
    public String toCell;
    public LocalDateTime createdAt;
    public String cellContext;
    public Float socPercent;
    public Boolean isCharging;
    public String assetType;

    public BalancingRecommendation() {}

    public BalancingRecommendation(Long id, Long assetId, String action, String fromCell, String toCell,
                                   LocalDateTime createdAt, String cellContext, Float socPercent,
                                   Boolean isCharging, String assetType) {
        this.id = id;
        this.assetId = assetId;
        this.action = action;
        this.fromCell = fromCell;
        this.toCell = toCell;
        this.createdAt = createdAt;
        this.cellContext = cellContext;
        this.socPercent = socPercent;
        this.isCharging = isCharging;
        this.assetType = assetType;
    }

    private static BalancingRecommendation from(Row row) {
        return new BalancingRecommendation(
                row.getLong("id"),
                row.getLong("assetId"),
                row.getString("action"),
                row.getString("fromCell"),
                row.getString("toCell"),
                row.getLocalDateTime("createdAt"),
                row.getString("cellContext"),
                row.getFloat("socPercent"),
                row.getBoolean("isCharging"),
                row.getString("assetType")
        );
    }

    private static final String SELECT_COLS =
            "id, assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType";

    public static Multi<BalancingRecommendation> findAll(MySQLPool client) {
        return client.query("SELECT " + SELECT_COLS + " FROM BalancingRecommendation ORDER BY createdAt DESC")
                .execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(BalancingRecommendation::from);
    }

    public static Uni<BalancingRecommendation> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT " + SELECT_COLS + " FROM BalancingRecommendation WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<BalancingRecommendation> findByFromCell(MySQLPool client, String fromCell) {
        return client.preparedQuery("SELECT " + SELECT_COLS
                + " FROM BalancingRecommendation WHERE fromCell = ? ORDER BY createdAt DESC")
                .execute(Tuple.of(fromCell))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(BalancingRecommendation::from);
    }

    public static Multi<BalancingRecommendation> findByTimeWindow(MySQLPool client, LocalDateTime from, LocalDateTime to) {
        return client.preparedQuery("SELECT " + SELECT_COLS
                + " FROM BalancingRecommendation WHERE createdAt >= ? AND createdAt <= ? ORDER BY createdAt DESC")
                .execute(Tuple.of(from, to))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(BalancingRecommendation::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery(
                "INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType) VALUES (?,?,?,?,?,?,?,?,?)")
                .execute(Tuple.from(Arrays.asList(assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType)))
                .onItem().transform(rowSet -> (Long) rowSet.property(MySQLClient.LAST_INSERTED_ID));
    }

    public static Uni<Boolean> update(MySQLPool client, Long id, Long assetId, String action,
                                      String fromCell, String toCell, LocalDateTime createdAt,
                                      String cellContext, Float socPercent, Boolean isCharging, String assetType) {
        return client.preparedQuery("UPDATE BalancingRecommendation SET assetId = ?, action = ?, "
                + "fromCell = ?, toCell = ?, createdAt = ?, cellContext = ?, socPercent = ?, isCharging = ?, assetType = ? WHERE id = ?")
                .execute(Tuple.from(Arrays.asList(assetId, action, fromCell, toCell, createdAt, cellContext, socPercent, isCharging, assetType, id)))
                .onItem().transform(rowSet -> rowSet.rowCount() == 1);
    }

    public static Uni<Boolean> delete(MySQLPool client, Long id) {
        return client.preparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(rowSet -> rowSet.rowCount() == 1);
    }
}
