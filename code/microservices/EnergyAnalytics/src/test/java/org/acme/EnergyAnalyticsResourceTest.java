package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;
import org.acme.dto.*;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;
import org.acme.services.AnalyticsCalculationService;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

class EnergyAnalyticsResourceTest {

    EnergyAnalyticsResource resource;
    private MySQLPool client;
    private AnalyticsCalculationService analyticsService;

    @BeforeEach
    void setup() {
        resource = new EnergyAnalyticsResource();
        client = Mockito.mock(MySQLPool.class);
        analyticsService = Mockito.mock(AnalyticsCalculationService.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
        injectService(resource, analyticsService);
    }

    @Test
    void getDischargedByZone_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = dischargedZoneRow(1L, "GRID_A", 100.5, 5, timestamp1, "CURRENT");
        Row row2 = dischargedZoneRow(2L, "GRID_B", 75.3, 3, timestamp2, "CURRENT");
        stubQuery("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<EnergyDischargedByZone> result = resource.getDischargedByZone().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(0).totalEnergyDischargedKwh, is(100.5));
        MatcherAssert.assertThat(result.get(0).batteryCount, is(5));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void getDischargedByGridCell_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = dischargedZoneRow(1L, "GRID_A", 100.5, 5, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE gridCellId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

        List<EnergyDischargedByZone> result = resource.getDischargedByGridCell("GRID_A").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(1));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("GRID_A"));
    }

    @Test
    void getGeneratedByProsumer_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = generatedProsumerRow(1L, 1L, 50.5, 2, timestamp1, "CURRENT");
        Row row2 = generatedProsumerRow(2L, 2L, 75.3, 3, timestamp2, "CURRENT");
        stubQuery("SELECT id, prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<GeneratedEnergyByProsumer> result = resource.getGeneratedByProsumer().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).totalEnergyGeneratedKwh, is(50.5));
        MatcherAssert.assertThat(result.get(0).solarAssetCount, is(2));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void getGeneratedByProsumerId_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = generatedProsumerRow(1L, 1L, 50.5, 2, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

        List<GeneratedEnergyByProsumer> result = resource.getGeneratedByProsumerId(1L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(1));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
    }

    @Test
    void getConsumedByProsumer_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = consumedProsumerRow(1L, 1L, 25.5, 1, timestamp1, "CURRENT");
        Row row2 = consumedProsumerRow(2L, 2L, 35.3, 2, timestamp2, "CURRENT");
        stubQuery("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<ConsumedEnergyByProsumer> result = resource.getConsumedByProsumer().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).totalEnergyConsumedKwh, is(25.5));
        MatcherAssert.assertThat(result.get(0).evChargerCount, is(1));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void getConsumedByProsumerId_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = consumedProsumerRow(1L, 2L, 25.5, 1, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

        List<ConsumedEnergyByProsumer> result = resource.getConsumedByProsumerId(2L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(1));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(2L));
    }

    @Test
    void getAverageSoC_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = averageSoCRow(1L, 75.5, 10, timestamp1, "CURRENT");
        Row row2 = averageSoCRow(2L, 80.3, 12, timestamp2, "CURRENT");
        stubQuery("SELECT id, averageSocPercent, batteryCount, timestamp, aggregationPeriod FROM AverageSoC ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<AverageSoC> result = resource.getAverageSoC().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).averageSocPercent, is(75.5));
        MatcherAssert.assertThat(result.get(0).batteryCount, is(10));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void computeGeneratedByProsumer_returnsList() {
        List<AssetDTO> assets = Arrays.asList(createAssetDTO(2L, 1L));
        List<TelemetryDTO> telemetry = Arrays.asList(createSolarTelemetry(2L, 100.0f));
        GeneratedEnergyByProsumer expected = new GeneratedEnergyByProsumer(null, 1L, 100.0, 1, LocalDateTime.now(), "LAST_30_MIN");

        Mockito.when(analyticsService.computeGeneratedByProsumer(assets, telemetry))
               .thenReturn(Arrays.asList(expected));

        Response response = resource.computeGeneratedByProsumer(new ComputeGeneratedRequest(assets, telemetry));
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    @Test
    void computeConsumedByProsumer_returnsList() {
        List<AssetDTO> assets = Arrays.asList(createAssetDTO(3L, 2L));
        List<TelemetryDTO> telemetry = Arrays.asList(createEVChargerTelemetry(3L, 25.0f));
        ConsumedEnergyByProsumer expected = new ConsumedEnergyByProsumer(null, 2L, 25.0, 1, LocalDateTime.now(), "LAST_30_MIN");

        Mockito.when(analyticsService.computeConsumedByProsumer(assets, telemetry))
               .thenReturn(Arrays.asList(expected));

        Response response = resource.computeConsumedByProsumer(new ComputeConsumedRequest(assets, telemetry));
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    @Test
    void computeDischargedByZone_returnsList() {
        List<GridCellDTO> zones = Arrays.asList(createGridCellDTO("GRID_A"));
        List<TelemetryDTO> telemetry = Arrays.asList(createBatteryTelemetry(1L, 50.0f, 75.0f));
        EnergyDischargedByZone expected = new EnergyDischargedByZone(null, "GRID_A", 50.0, 1, LocalDateTime.now(), "LAST_30_MIN");

        Mockito.when(analyticsService.computeDischargedByZone(zones, telemetry))
               .thenReturn(Arrays.asList(expected));

        Response response = resource.computeDischargedByZone(new ComputeDischargedRequest(zones, telemetry));
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    @Test
    void computeAverageSoC_returnsResult() {
        List<TelemetryDTO> telemetry = Arrays.asList(createBatteryTelemetry(1L, 50.0f, 75.0f));
        AverageSoC expected = new AverageSoC(null, 75.0, 1, LocalDateTime.now(), "LAST_30_MIN");

        Mockito.when(analyticsService.computeAverageSoC(telemetry)).thenReturn(expected);

        Response response = resource.computeAverageSoC(new ComputeAverageSoCRequest(telemetry));
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    @Test
    void persistConsumed_returnsSuccess() {
        ConsumedEnergyByProsumer entity = new ConsumedEnergyByProsumer(1L, 1L, 25.0, 1, LocalDateTime.now(), "LAST_30_MIN");
        PersistConsumedRequest request = new PersistConsumedRequest();
        request.consumedByProsumer = Arrays.asList(entity);

        Mockito.when(analyticsService.persistConsumed(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(Arrays.asList(entity)));

        Response response = resource.persistConsumed(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        @SuppressWarnings("unchecked")
        List<ConsumedEnergyByProsumer> result = (List<ConsumedEnergyByProsumer>) response.getEntity();
        MatcherAssert.assertThat(result.size(), is(1));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
    }

    @Test
    void persistGenerated_returnsSuccess() {
        GeneratedEnergyByProsumer entity = new GeneratedEnergyByProsumer(2L, 1L, 50.0, 2, LocalDateTime.now(), "LAST_30_MIN");
        PersistGeneratedRequest request = new PersistGeneratedRequest();
        request.generatedByProsumer = Arrays.asList(entity);

        Mockito.when(analyticsService.persistGenerated(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(Arrays.asList(entity)));

        Response response = resource.persistGenerated(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        @SuppressWarnings("unchecked")
        List<GeneratedEnergyByProsumer> result = (List<GeneratedEnergyByProsumer>) response.getEntity();
        MatcherAssert.assertThat(result.size(), is(1));
        MatcherAssert.assertThat(result.get(0).id, is(2L));
    }

    @Test
    void persistDischarged_returnsSuccess() {
        EnergyDischargedByZone entity = new EnergyDischargedByZone(3L, "GRID_A", 10.0, 3, LocalDateTime.now(), "LAST_30_MIN");
        PersistDischargedRequest request = new PersistDischargedRequest();
        request.dischargedByZone = Arrays.asList(entity);

        Mockito.when(analyticsService.persistDischarged(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(Arrays.asList(entity)));

        Response response = resource.persistDischarged(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        @SuppressWarnings("unchecked")
        List<EnergyDischargedByZone> result = (List<EnergyDischargedByZone>) response.getEntity();
        MatcherAssert.assertThat(result.size(), is(1));
        MatcherAssert.assertThat(result.get(0).id, is(3L));
    }

    @Test
    void persistAverage_returnsSuccess() {
        AverageSoC avgSoC = new AverageSoC(4L, 80.0, 5, LocalDateTime.now(), "LAST_30_MIN");
        PersistAverageSoCRequest request = new PersistAverageSoCRequest();
        request.averageSoC = avgSoC;

        Mockito.when(analyticsService.persistAverageSoC(Mockito.any()))
               .thenReturn(Uni.createFrom().item(avgSoC));

        Response response = resource.persistAverage(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        AverageSoC result = (AverageSoC) response.getEntity();
        MatcherAssert.assertThat(result.id, is(4L));
        MatcherAssert.assertThat(result.averageSocPercent, is(80.0));
    }

    private void injectClient(EnergyAnalyticsResource target, MySQLPool pool) {
        try {
            Field field = EnergyAnalyticsResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(EnergyAnalyticsResource target, boolean value) {
        try {
            Field field = EnergyAnalyticsResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
    }

    private void injectService(EnergyAnalyticsResource target, AnalyticsCalculationService service) {
        try {
            Field field = EnergyAnalyticsResource.class.getDeclaredField("analyticsService");
            field.setAccessible(true);
            field.set(target, service);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject AnalyticsCalculationService", e);
        }
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetWithRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Arrays.asList(rows)));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private Row dischargedZoneRow(Long id, String gridCellId, Double totalEnergyDischargedKwh,
                                  Integer batteryCount, LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("gridCellId")).thenReturn(gridCellId);
        Mockito.when(row.getDouble("totalEnergyDischargedKwh")).thenReturn(totalEnergyDischargedKwh);
        Mockito.when(row.getInteger("batteryCount")).thenReturn(batteryCount);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("aggregationPeriod")).thenReturn(aggregationPeriod);
        return row;
    }

    private Row generatedProsumerRow(Long id, Long prosumerId, Double totalEnergyGeneratedKwh,
                                     Integer solarAssetCount, LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getDouble("totalEnergyGeneratedKwh")).thenReturn(totalEnergyGeneratedKwh);
        Mockito.when(row.getInteger("solarAssetCount")).thenReturn(solarAssetCount);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("aggregationPeriod")).thenReturn(aggregationPeriod);
        return row;
    }

    private Row consumedProsumerRow(Long id, Long prosumerId, Double totalEnergyConsumedKwh,
                                    Integer evChargerCount, LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getDouble("totalEnergyConsumedKwh")).thenReturn(totalEnergyConsumedKwh);
        Mockito.when(row.getInteger("evChargerCount")).thenReturn(evChargerCount);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("aggregationPeriod")).thenReturn(aggregationPeriod);
        return row;
    }

    private Row averageSoCRow(Long id, Double averageSocPercent, Integer batteryCount,
                             LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getDouble("averageSocPercent")).thenReturn(averageSocPercent);
        Mockito.when(row.getInteger("batteryCount")).thenReturn(batteryCount);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("aggregationPeriod")).thenReturn(aggregationPeriod);
        return row;
    }

    private TelemetryDTO createBatteryTelemetry(Long assetId, Float currentOutput, Float soc) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.asset_type = "BATTERY";
        dto.Current_Output = currentOutput;
        dto.State_of_Charge = soc;
        dto.timeStamp = LocalDateTime.now();
        return dto;
    }

    private TelemetryDTO createSolarTelemetry(Long assetId, Float currentGeneration) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.asset_type = "SOLAR";
        dto.Current_Generation = currentGeneration;
        dto.timeStamp = LocalDateTime.now();
        return dto;
    }

    private TelemetryDTO createEVChargerTelemetry(Long assetId, Float chargingRate) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.asset_type = "EV_CHARGER";
        dto.Charging_Rate = chargingRate;
        dto.timeStamp = LocalDateTime.now();
        return dto;
    }

    private AssetDTO createAssetDTO(Long assetId, Long prosumerId) {
        return new AssetDTO(assetId, prosumerId);
    }

    private GridCellDTO createGridCellDTO(String gridCellId) {
        return new GridCellDTO(gridCellId, 1L, 50.0, "Test Area");
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        private ListRowIterator(List<Row> rows) {
            this.iterator = rows.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Row next() {
            return iterator.next();
        }
    }
}
