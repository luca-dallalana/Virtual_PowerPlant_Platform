package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class FlexibilityEvent {

    public Long id;
    public Long assetId;
    public Long prosumerId;
    public String eventType;
    public Float soc_percent;
    public String recommendedAction;
    public Float marketPrice;
    public Float incentiveAmount;
    public String gridCellId;
    public LocalDateTime timestamp;

    public FlexibilityEvent() {
    }

    public FlexibilityEvent(Long id, Long assetId, Long prosumerId, String eventType,
                           Float soc_percent, String recommendedAction, Float marketPrice,
                           Float incentiveAmount, String gridCellId, LocalDateTime timestamp) {
        this.id = id;
        this.assetId = assetId;
        this.prosumerId = prosumerId;
        this.eventType = eventType;
        this.soc_percent = soc_percent;
        this.recommendedAction = recommendedAction;
        this.marketPrice = marketPrice;
        this.incentiveAmount = incentiveAmount;
        this.gridCellId = gridCellId;
        this.timestamp = timestamp;
    }

    private static FlexibilityEvent from(Row row) {
        return new FlexibilityEvent(
            row.getLong("id"),
            row.getLong("assetId"),
            row.getLong("prosumerId"),
            row.getString("eventType"),
            row.getFloat("soc_percent"),
            row.getString("recommendedAction"),
            row.getFloat("marketPrice"),
            row.getFloat("incentiveAmount"),
            row.getString("gridCellId"),
            row.getLocalDateTime("timestamp")
        );
    }

    public static Multi<FlexibilityEvent> findAll(MySQLPool client) {
        return client.query("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent ORDER BY timestamp DESC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(FlexibilityEvent::from);
    }

    public static Uni<FlexibilityEvent> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<FlexibilityEvent> findByAssetId(MySQLPool client, Long assetId) {
        return client.preparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE assetId = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(assetId))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(FlexibilityEvent::from);
    }

    public static Multi<FlexibilityEvent> findByEventType(MySQLPool client, String eventType) {
        return client.preparedQuery("SELECT id, assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp FROM FlexibilityEvent WHERE eventType = ? ORDER BY timestamp DESC")
                .execute(Tuple.of(eventType))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(FlexibilityEvent::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO FlexibilityEvent(assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp) VALUES (?,?,?,?,?,?,?,?,?)")
                .execute(Tuple.of(assetId, prosumerId, eventType, soc_percent, recommendedAction, marketPrice, incentiveAmount, gridCellId, timestamp))
                .onItem().transform(pgRowSet -> pgRowSet.property(io.vertx.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }
}
