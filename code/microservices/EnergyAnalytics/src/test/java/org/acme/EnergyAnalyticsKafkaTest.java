package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import org.acme.dto.AssetDTO;
import org.acme.dto.GridCellDTO;
import org.acme.dto.TelemetryDTO;
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
import java.util.List;

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

        java.util.List<ConsumedEnergyByProsumer> result = service.persistConsumed(Arrays.asList(c1, c2)).await().indefinitely();

        assertThat(result.size(), is(2));
    }

    @Test
    void persistConsumed_emptyList_returnsEmpty() {
        java.util.List<ConsumedEnergyByProsumer> result = service.persistConsumed(Collections.emptyList()).await().indefinitely();
        assertThat(result.size(), is(0));
    }

    @Test
    void persistGenerated_savesAllRecords() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        GeneratedEnergyByProsumer g1 = new GeneratedEnergyByProsumer(null, 1L, 50.0, 2, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO GeneratedEnergyByProsumer(prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 1L);

        java.util.List<GeneratedEnergyByProsumer> result = service.persistGenerated(Collections.singletonList(g1)).await().indefinitely();

        assertThat(result.size(), is(1));
        assertThat(result.get(0).id, is(1L));
    }

    @Test
    void persistGenerated_emptyList_returnsEmpty() {
        java.util.List<GeneratedEnergyByProsumer> result = service.persistGenerated(Collections.emptyList()).await().indefinitely();
        assertThat(result.size(), is(0));
    }

    @Test
    void persistDischarged_savesAllRecords() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        EnergyDischargedByZone d1 = new EnergyDischargedByZone(null, "GRID_A", 10.0, 3, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO EnergyDischargedByZone(gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?,?)", 1L);

        java.util.List<EnergyDischargedByZone> result = service.persistDischarged(Collections.singletonList(d1)).await().indefinitely();

        assertThat(result.size(), is(1));
        assertThat(result.get(0).id, is(1L));
    }

    @Test
    void persistDischarged_emptyList_returnsEmpty() {
        java.util.List<EnergyDischargedByZone> result = service.persistDischarged(Collections.emptyList()).await().indefinitely();
        assertThat(result.size(), is(0));
    }

    @Test
    void persistAverageSoC_savesRecord() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        AverageSoC avgSoC = new AverageSoC(null, 80.0, 5, ts, "LAST_30_MIN");
        stubInsert("INSERT INTO AverageSoC(averageSocPercent, batteryCount, timestamp, aggregationPeriod) VALUES (?,?,?,?)", 1L);

        AverageSoC result = service.persistAverageSoC(avgSoC).await().indefinitely();

        assertThat(result.id, is(1L));
        assertThat(result.averageSocPercent, is(80.0));
    }

    @Test
    void persistAverageSoC_nullInput_returnsNull() {
        AverageSoC result = service.persistAverageSoC(null).await().indefinitely();
        assertThat(result == null, is(true));
    }

    // --- computeGeneratedByProsumer ---

    @Test
    void computeGeneratedByProsumer_singleSolarAsset_returnsCorrectEnergy() {
        AssetDTO asset = new AssetDTO(2L, 1L);
        TelemetryDTO t = solarTelemetry(2L, 100.0f);

        List<GeneratedEnergyByProsumer> result = service.computeGeneratedByProsumer(
            Collections.singletonList(asset), Collections.singletonList(t));

        assertThat(result.size(), is(1));
        assertThat(result.get(0).prosumerId, is(1L));
        assertThat(result.get(0).totalEnergyGeneratedKwh, is(50.0)); // 100.0 kW * 0.5h
        assertThat(result.get(0).solarAssetCount, is(1));
    }

    @Test
    void computeGeneratedByProsumer_twoAssetsOneProsumer_sumsEnergy() {
        List<AssetDTO> assets = Arrays.asList(new AssetDTO(2L, 1L), new AssetDTO(3L, 1L));
        List<TelemetryDTO> telemetry = Arrays.asList(solarTelemetry(2L, 100.0f), solarTelemetry(3L, 60.0f));

        List<GeneratedEnergyByProsumer> result = service.computeGeneratedByProsumer(assets, telemetry);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).prosumerId, is(1L));
        assertThat(result.get(0).totalEnergyGeneratedKwh, is(80.0)); // 50.0 + 30.0
        assertThat(result.get(0).solarAssetCount, is(2));
    }

    @Test
    void computeGeneratedByProsumer_nonSolarFiltered() {
        AssetDTO asset = new AssetDTO(1L, 1L);

        List<GeneratedEnergyByProsumer> result = service.computeGeneratedByProsumer(
            Collections.singletonList(asset),
            Collections.singletonList(batteryTelemetry(1L, 50.0f, 80.0f, null)));

        assertThat(result.size(), is(0));
    }

    @Test
    void computeGeneratedByProsumer_emptyTelemetry_returnsEmpty() {
        List<GeneratedEnergyByProsumer> result = service.computeGeneratedByProsumer(
            Collections.singletonList(new AssetDTO(2L, 1L)), Collections.emptyList());

        assertThat(result.size(), is(0));
    }

    // --- computeConsumedByProsumer ---

    @Test
    void computeConsumedByProsumer_singleEvCharger_returnsCorrectEnergy() {
        AssetDTO asset = new AssetDTO(3L, 2L);
        TelemetryDTO t = evChargerTelemetry(3L, 25.0f);

        List<ConsumedEnergyByProsumer> result = service.computeConsumedByProsumer(
            Collections.singletonList(asset), Collections.singletonList(t));

        assertThat(result.size(), is(1));
        assertThat(result.get(0).prosumerId, is(2L));
        assertThat(result.get(0).totalEnergyConsumedKwh, is(12.5)); // 25.0 kW * 0.5h
        assertThat(result.get(0).evChargerCount, is(1));
    }

    @Test
    void computeConsumedByProsumer_nonEvChargerFiltered() {
        AssetDTO asset = new AssetDTO(2L, 1L);

        List<ConsumedEnergyByProsumer> result = service.computeConsumedByProsumer(
            Collections.singletonList(asset),
            Collections.singletonList(solarTelemetry(2L, 100.0f)));

        assertThat(result.size(), is(0));
    }

    // --- computeDischargedByZone ---

    @Test
    void computeDischargedByZone_singleBattery_returnsCorrectEnergy() {
        GridCellDTO zone = new GridCellDTO("GRID_A", 1L, 50.0, "Test");
        TelemetryDTO t = batteryTelemetry(1L, 50.0f, 80.0f, "GRID_A");

        List<EnergyDischargedByZone> result = service.computeDischargedByZone(
            Collections.singletonList(zone), Collections.singletonList(t));

        assertThat(result.size(), is(1));
        assertThat(result.get(0).gridCellId, is("GRID_A"));
        assertThat(result.get(0).totalEnergyDischargedKwh, is(25.0)); // 50.0 kW * 0.5h
        assertThat(result.get(0).batteryCount, is(1));
    }

    @Test
    void computeDischargedByZone_negativeOutput_treatedAsZero() {
        GridCellDTO zone = new GridCellDTO("GRID_A", 1L, 50.0, "Test");
        TelemetryDTO t = batteryTelemetry(1L, -10.0f, 80.0f, "GRID_A");

        List<EnergyDischargedByZone> result = service.computeDischargedByZone(
            Collections.singletonList(zone), Collections.singletonList(t));

        assertThat(result.size(), is(1));
        assertThat(result.get(0).totalEnergyDischargedKwh, is(0.0));
    }

    @Test
    void computeDischargedByZone_noMatchingAssets_emitsZeroRecord() {
        GridCellDTO zone = new GridCellDTO("GRID_B", 2L, 75.0, "Industrial");

        List<EnergyDischargedByZone> result = service.computeDischargedByZone(
            Collections.singletonList(zone), Collections.emptyList());

        assertThat(result.size(), is(1));
        assertThat(result.get(0).gridCellId, is("GRID_B"));
        assertThat(result.get(0).totalEnergyDischargedKwh, is(0.0));
        assertThat(result.get(0).batteryCount, is(0));
    }

    @Test
    void computeDischargedByZone_unknownGridCell_excluded() {
        GridCellDTO zone = new GridCellDTO("GRID_A", 1L, 50.0, "Test");
        TelemetryDTO t = batteryTelemetry(1L, 50.0f, 80.0f, "GRID_X");

        List<EnergyDischargedByZone> result = service.computeDischargedByZone(
            Collections.singletonList(zone), Collections.singletonList(t));

        assertThat(result.size(), is(1));
        assertThat(result.get(0).totalEnergyDischargedKwh, is(0.0));
        assertThat(result.get(0).batteryCount, is(0));
    }

    // --- computeAverageSoC ---

    @Test
    void computeAverageSoC_multipleReadingsSameAsset_averagesPerAsset() {
        TelemetryDTO t1 = batteryTelemetry(1L, 50.0f, 80.0f, null);
        TelemetryDTO t2 = batteryTelemetry(1L, 40.0f, 60.0f, null);

        AverageSoC result = service.computeAverageSoC(Arrays.asList(t1, t2));

        assertThat(result.averageSocPercent, is(70.0)); // (80+60)/2 for asset 1
        assertThat(result.batteryCount, is(1));
    }

    @Test
    void computeAverageSoC_twoAssets_averagesAcrossAssets() {
        TelemetryDTO t1 = batteryTelemetry(1L, 50.0f, 80.0f, null);
        TelemetryDTO t2 = batteryTelemetry(2L, 40.0f, 60.0f, null);

        AverageSoC result = service.computeAverageSoC(Arrays.asList(t1, t2));

        assertThat(result.averageSocPercent, is(70.0)); // (80+60)/2 across assets
        assertThat(result.batteryCount, is(2));
    }

    @Test
    void computeAverageSoC_noBattery_returnsZero() {
        AverageSoC result = service.computeAverageSoC(
            Collections.singletonList(solarTelemetry(2L, 100.0f)));

        assertThat(result.averageSocPercent, is(0.0));
        assertThat(result.batteryCount, is(0));
    }

    // --- helpers ---

    private TelemetryDTO solarTelemetry(Long assetId, Float currentGeneration) {
        TelemetryDTO t = new TelemetryDTO();
        t.asset_id = assetId;
        t.asset_type = "SOLAR";
        t.Current_Generation = currentGeneration;
        t.timeStamp = LocalDateTime.now();
        return t;
    }

    private TelemetryDTO evChargerTelemetry(Long assetId, Float chargingRate) {
        TelemetryDTO t = new TelemetryDTO();
        t.asset_id = assetId;
        t.asset_type = "EV_CHARGER";
        t.Charging_Rate = chargingRate;
        t.timeStamp = LocalDateTime.now();
        return t;
    }

    private TelemetryDTO batteryTelemetry(Long assetId, Float currentOutput, Float stateOfCharge, String gridCellId) {
        TelemetryDTO t = new TelemetryDTO();
        t.asset_id = assetId;
        t.asset_type = "BATTERY";
        t.Current_Output = currentOutput;
        t.State_of_Charge = stateOfCharge;
        t.grid_cell_id = gridCellId;
        t.timeStamp = LocalDateTime.now();
        return t;
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
