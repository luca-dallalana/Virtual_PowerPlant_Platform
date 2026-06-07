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
    public Float successRate;
    public String dominantSentiment;
    public Integer totalEventsAnalyzed;
    public String analyzedEventIds;
    public LocalDateTime createdAt;

    public ForecastingResult() {}

    private static ForecastingResult from(Row row) {
        ForecastingResult r = new ForecastingResult();
        r.id = row.getLong("id");
        r.successRate = row.getFloat("successRate");
        r.dominantSentiment = row.getString("dominantSentiment");
        r.totalEventsAnalyzed = row.getInteger("totalEventsAnalyzed");
        r.analyzedEventIds = row.getString("analyzedEventIds");
        r.createdAt = row.getLocalDateTime("createdAt");
        return r;
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery(
                "INSERT INTO ForecastingResult(successRate, dominantSentiment, totalEventsAnalyzed, analyzedEventIds, createdAt) VALUES (?,?,?,?,?)")
                .execute(Tuple.from(Arrays.asList(successRate, dominantSentiment, totalEventsAnalyzed, analyzedEventIds, createdAt)))
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
                .onItem().transform(rs -> rs.rowCount() == 1);
    }
}
