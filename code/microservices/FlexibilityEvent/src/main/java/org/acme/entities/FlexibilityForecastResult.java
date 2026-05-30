package org.acme.entities;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLClient;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.Arrays;

public class FlexibilityForecastResult {

    public Long id;
    public String windowStart;
    public String windowEnd;
    public Integer flexibilityEventsCount;
    public Integer gridBalancingCount;
    public String forecastResult;
    public LocalDateTime createdAt;

    public FlexibilityForecastResult() {}

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery(
            "INSERT INTO FlexibilityForecastResult(" +
            "windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, forecastResult, createdAt) " +
            "VALUES (?,?,?,?,?,?)"
        ).execute(Tuple.from(Arrays.asList(windowStart, windowEnd, flexibilityEventsCount, gridBalancingCount, forecastResult, createdAt)))
         .onItem().transform(pgRowSet -> (Long) pgRowSet.property(MySQLClient.LAST_INSERTED_ID));
    }
}
