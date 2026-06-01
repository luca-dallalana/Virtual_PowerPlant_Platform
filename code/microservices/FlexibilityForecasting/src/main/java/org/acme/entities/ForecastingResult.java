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

public class ForecastingResult {

    public Long id;
    public String forecastResult;
    public String windowStart;
    public String windowEnd;
    public Integer flexibilityEventsCount;
    public Integer gridBalancingCount;
    public LocalDateTime createdAt;

    public ForecastingResult() {}

    private static ForecastingResult from(Row row) {
        ForecastingResult r = new ForecastingResult();
        r.id = row.getLong("id");
        r.forecastResult = row.getString("forecastResult");
        r.windowStart = row.getString("windowStart");
        r.windowEnd = row.getString("windowEnd");
        r.flexibilityEventsCount = row.getInteger("flexibilityEventsCount");
        r.gridBalancingCount = row.getInteger("gridBalancingCount");
        r.createdAt = row.getLocalDateTime("createdAt");
        return r;
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery(
                "INSERT INTO ForecastingResult(forecastResult, windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, createdAt) VALUES (?,?,?,?,?,?)")
                .execute(Tuple.from(Arrays.asList(forecastResult, windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, createdAt)))
                .onItem().transform(rs -> (Long) rs.property(MySQLClient.LAST_INSERTED_ID));
    }

    public static Multi<ForecastingResult> findAll(MySQLPool client) {
        return client.query("SELECT * FROM ForecastingResult ORDER BY createdAt DESC").execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(ForecastingResult::from);
    }

    public static Uni<ForecastingResult> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT * FROM ForecastingResult WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Uni<Boolean> delete(MySQLPool client, Long id) {
        return client.preparedQuery("DELETE FROM ForecastingResult WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}
