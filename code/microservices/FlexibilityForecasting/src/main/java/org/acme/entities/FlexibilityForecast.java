package org.acme.entities;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.time.LocalDateTime;

public class FlexibilityForecast {

    public Long id;
    public String analysisType;
    public String question;
    public String aiResponse;
    public String sentiment;
    public Double confidenceScore;
    public Integer eventsAnalyzed;
    public Integer correlatedOutcomes;
    public Double successRate;
    public LocalDateTime analysisTimestamp;
    public String insightsJson;

    public FlexibilityForecast() {
    }

    public FlexibilityForecast(Long id, String analysisType, String question, String aiResponse,
                              String sentiment, Double confidenceScore, Integer eventsAnalyzed,
                              Integer correlatedOutcomes, Double successRate, LocalDateTime analysisTimestamp,
                              String insightsJson) {
        this.id = id;
        this.analysisType = analysisType;
        this.question = question;
        this.aiResponse = aiResponse;
        this.sentiment = sentiment;
        this.confidenceScore = confidenceScore;
        this.eventsAnalyzed = eventsAnalyzed;
        this.correlatedOutcomes = correlatedOutcomes;
        this.successRate = successRate;
        this.analysisTimestamp = analysisTimestamp;
        this.insightsJson = insightsJson;
    }

    @Override
    public String toString() {
        return "{id:" + id + ", analysisType:" + analysisType + ", question:" + question
                + ", aiResponse:" + aiResponse + ", sentiment:" + sentiment
                + ", confidenceScore:" + confidenceScore + ", eventsAnalyzed:" + eventsAnalyzed
                + ", correlatedOutcomes:" + correlatedOutcomes + ", successRate:" + successRate
                + ", analysisTimestamp:" + analysisTimestamp + ", insightsJson:" + insightsJson + "}\n";
    }

    private static FlexibilityForecast from(Row row) {
        return new FlexibilityForecast(
                row.getLong("id"),
                row.getString("analysisType"),
                row.getString("question"),
                row.getString("aiResponse"),
                row.getString("sentiment"),
                row.getDouble("confidenceScore"),
                row.getInteger("eventsAnalyzed"),
                row.getInteger("correlatedOutcomes"),
                row.getDouble("successRate"),
                row.getLocalDateTime("analysisTimestamp"),
                row.getString("insightsJson")
        );
    }

    public static Multi<FlexibilityForecast> findAll(MySQLPool client) {
        return client.query("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, "
                + "eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson "
                + "FROM FlexibilityForecast ORDER BY analysisTimestamp DESC")
                .execute()
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(FlexibilityForecast::from);
    }

    public static Uni<FlexibilityForecast> findById(MySQLPool client, Long id) {
        return client.preparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, "
                + "eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson "
                + "FROM FlexibilityForecast WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(RowSet::iterator)
                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
    }

    public static Multi<FlexibilityForecast> findByAnalysisType(MySQLPool client, String analysisType) {
        return client.preparedQuery("SELECT id, analysisType, question, aiResponse, sentiment, confidenceScore, "
                + "eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson "
                + "FROM FlexibilityForecast WHERE analysisType = ? ORDER BY analysisTimestamp DESC")
                .execute(Tuple.of(analysisType))
                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
                .onItem().transform(FlexibilityForecast::from);
    }

    public Uni<Long> save(MySQLPool client) {
        return client.preparedQuery("INSERT INTO FlexibilityForecast("
                + "analysisType, question, aiResponse, sentiment, confidenceScore, eventsAnalyzed, "
                + "correlatedOutcomes, successRate, analysisTimestamp, insightsJson) VALUES (?,?,?,?,?,?,?,?,?,?)")
                .execute(Tuple.from(java.util.Arrays.asList(analysisType, question, aiResponse, sentiment, confidenceScore,
                        eventsAnalyzed, correlatedOutcomes, successRate, analysisTimestamp, insightsJson)))
                .onItem().transform(pgRowSet -> (Long) pgRowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID));
    }

    public static Uni<Boolean> delete(MySQLPool client, Long id) {
        return client.preparedQuery("DELETE FROM FlexibilityForecast WHERE id = ?")
                .execute(Tuple.of(id))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
    }
}
