package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import org.acme.dto.AnalyticsResult;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;
import org.acme.services.AnalyticsCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class EnergyAnalyticsKafkaTest {

    private AnalyticsCalculationService service;
    private MySQLPool client;

    @BeforeEach
    void setup() {
        service = new AnalyticsCalculationService();
        client = Mockito.mock(MySQLPool.class);
        injectClient(service, client);
    }

    @Test
    void persistConsumed_savesAllRecords() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        ConsumedEnergyByProsumer c1 = new ConsumedEnergyByProsumer(null, 1L, 20.0, 1, ts, "LAST_30_MIN");
        ConsumedEnergyByProsumer c2 = new ConsumedEnergyByProsumer(null, 2L, 30.0, 2, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO ConsumedEnergyByProsumer(prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 1L);

        AnalyticsResult result = service.persistConsumed(Arrays.asList(c1, c2)).await().indefinitely();

        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(2));
    }

    @Test
    void persistConsumed_emptyList_returnsZero() {
        AnalyticsResult result = service.persistConsumed(Collections.emptyList()).await().indefinitely();
        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(0));
    }

    @Test
    void persistGenerated_savesAllRecords() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        GeneratedEnergyByProsumer g1 = new GeneratedEnergyByProsumer(null, 1L, 50.0, 2, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO GeneratedEnergyByProsumer(prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 1L);

        AnalyticsResult result = service.persistGenerated(Collections.singletonList(g1)).await().indefinitely();

        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(1));
    }

    @Test
    void persistGenerated_emptyList_returnsZero() {
        AnalyticsResult result = service.persistGenerated(Collections.emptyList()).await().indefinitely();
        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(0));
    }

    @Test
    void persistDischarged_savesAllRecords() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        EnergyDischargedByZone d1 = new EnergyDischargedByZone(null, "GRID_A", 10.0, 3, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO EnergyDischargedByZone(gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 1L);

        AnalyticsResult result = service.persistDischarged(Collections.singletonList(d1)).await().indefinitely();

        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(1));
    }

    @Test
    void persistDischarged_emptyList_returnsZero() {
        AnalyticsResult result = service.persistDischarged(Collections.emptyList()).await().indefinitely();
        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(0));
    }

    @Test
    void persistAverageSoC_savesRecord() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        AverageSoC avgSoC = new AverageSoC(null, 80.0, 5, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO AverageSoC(averageSocPercent, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?)", 1L);

        AnalyticsResult result = service.persistAverageSoC(avgSoC).await().indefinitely();

        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(1));
    }

    @Test
    void persistAverageSoC_nullInput_returnsZero() {
        AnalyticsResult result = service.persistAverageSoC(null).await().indefinitely();
        assertThat(result.status, is("SUCCESS"));
        assertThat(result.recordsProcessed, is(0));
    }

    private void stubInsert(String sql, Long insertedId) {
        RowSet<Row> insertResult = Mockito.mock(RowSet.class);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(insertedId);
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(insertResult));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private void injectClient(AnalyticsCalculationService target, MySQLPool pool) {
        try {
            Field field = AnalyticsCalculationService.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }
}
