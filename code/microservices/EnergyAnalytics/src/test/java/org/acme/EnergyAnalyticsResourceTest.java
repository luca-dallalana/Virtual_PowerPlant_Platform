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
import org.acme.dto.AnalyticsRequest;
import org.acme.dto.AnalyticsResult;
import org.acme.dto.AssetLinkDTO;
import org.acme.dto.TelemetryDTO;
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
import java.util.Collections;
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
        stubQuery("SELECT id, gridCellId, totalEnergyDischargedKw, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<EnergyDischargedByZone> result = resource.getDischargedByZone().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(0).totalEnergyDischargedKw, is(100.5));
        MatcherAssert.assertThat(result.get(0).batteryCount, is(5));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void getDischargedByGridCell_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = dischargedZoneRow(1L, "GRID_A", 100.5, 5, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, gridCellId, totalEnergyDischargedKw, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE gridCellId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

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
        stubQuery("SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<GeneratedEnergyByProsumer> result = resource.getGeneratedByProsumer().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).totalEnergyGeneratedKw, is(50.5));
        MatcherAssert.assertThat(result.get(0).solarAssetCount, is(2));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void getGeneratedByProsumerId_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = generatedProsumerRow(1L, 1L, 50.5, 2, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, prosumerId, totalEnergyGeneratedKw, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

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
        stubQuery("SELECT id, prosumerId, totalEnergyConsumedKw, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        List<ConsumedEnergyByProsumer> result = resource.getConsumedByProsumer().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).totalEnergyConsumedKw, is(25.5));
        MatcherAssert.assertThat(result.get(0).evChargerCount, is(1));
        MatcherAssert.assertThat(result.get(0).timestamp, is(timestamp1));
        MatcherAssert.assertThat(result.get(0).aggregationPeriod, is("CURRENT"));
    }

    @Test
    void getConsumedByProsumerId_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = consumedProsumerRow(1L, 2L, 25.5, 1, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, prosumerId, totalEnergyConsumedKw, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

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
    void evaluate_withAllAssetTypes_createsRecords() {
        List<TelemetryDTO> telemetry = Arrays.asList(
            createBatteryTelemetry(1L, 50.0f, 75.0f),
            createSolarTelemetry(2L, 100.0f),
            createEVChargerTelemetry(3L, 25.0f)
        );
        List<AssetLinkDTO> assetLinks = Arrays.asList(
            createAssetLink(1L, 1L, "GRID_A"),
            createAssetLink(2L, 1L, "GRID_A"),
            createAssetLink(3L, 2L, "GRID_B")
        );

        AnalyticsResult expectedResult = new AnalyticsResult();
        expectedResult.status = "SUCCESS";
        expectedResult.recordsProcessed = 3;
        expectedResult.timestamp = LocalDateTime.now();

        Mockito.when(analyticsService.calculateMetricsFromEvents(telemetry, assetLinks))
               .thenReturn(Uni.createFrom().item(expectedResult));

        AnalyticsRequest request = new AnalyticsRequest(telemetry, assetLinks);

        Response response = resource.evaluate(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        AnalyticsResult result = (AnalyticsResult) response.getEntity();
        MatcherAssert.assertThat(result.status, is("SUCCESS"));
        MatcherAssert.assertThat(result.recordsProcessed, is(3));
    }

    @Test
    void evaluate_withPartialData_createsRecords() {
        List<TelemetryDTO> telemetry = Arrays.asList(
            createBatteryTelemetry(1L, 50.0f, 75.0f)
        );
        List<AssetLinkDTO> assetLinks = Arrays.asList(
            createAssetLink(1L, 1L, "GRID_A")
        );

        AnalyticsResult expectedResult = new AnalyticsResult();
        expectedResult.status = "SUCCESS";
        expectedResult.recordsProcessed = 1;
        expectedResult.timestamp = LocalDateTime.now();

        Mockito.when(analyticsService.calculateMetricsFromEvents(telemetry, assetLinks))
               .thenReturn(Uni.createFrom().item(expectedResult));

        AnalyticsRequest request = new AnalyticsRequest(telemetry, assetLinks);

        Response response = resource.evaluate(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        AnalyticsResult result = (AnalyticsResult) response.getEntity();
        MatcherAssert.assertThat(result.status, is("SUCCESS"));
        MatcherAssert.assertThat(result.recordsProcessed, is(1));
    }

    @Test
    void evaluate_withEmptyData_returnsZeroRecords() {
        List<TelemetryDTO> telemetry = Collections.emptyList();
        List<AssetLinkDTO> assetLinks = Collections.emptyList();

        AnalyticsResult expectedResult = new AnalyticsResult();
        expectedResult.status = "SUCCESS";
        expectedResult.recordsProcessed = 0;
        expectedResult.timestamp = LocalDateTime.now();

        Mockito.when(analyticsService.calculateMetricsFromEvents(telemetry, assetLinks))
               .thenReturn(Uni.createFrom().item(expectedResult));

        AnalyticsRequest request = new AnalyticsRequest(telemetry, assetLinks);

        Response response = resource.evaluate(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        AnalyticsResult result = (AnalyticsResult) response.getEntity();
        MatcherAssert.assertThat(result.recordsProcessed, is(0));
    }

    @Test
    void evaluate_withNullFieldsInTelemetry_handlesGracefully() {
        TelemetryDTO telemetry = createBatteryTelemetry(1L, null, 75.0f);
        List<AssetLinkDTO> assetLinks = Arrays.asList(
            createAssetLink(1L, 1L, "GRID_A")
        );

        AnalyticsResult expectedResult = new AnalyticsResult();
        expectedResult.status = "SUCCESS";
        expectedResult.recordsProcessed = 0;
        expectedResult.timestamp = LocalDateTime.now();

        Mockito.when(analyticsService.calculateMetricsFromEvents(Arrays.asList(telemetry), assetLinks))
               .thenReturn(Uni.createFrom().item(expectedResult));

        AnalyticsRequest request = new AnalyticsRequest(Arrays.asList(telemetry), assetLinks);

        Response response = resource.evaluate(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        Mockito.verify(analyticsService, Mockito.times(1)).calculateMetricsFromEvents(Arrays.asList(telemetry), assetLinks);
    }

    @Test
    void evaluate_withUnlinkedAssets_filtersCorrectly() {
        TelemetryDTO telemetry = createBatteryTelemetry(99L, 50.0f, 75.0f);
        List<AssetLinkDTO> assetLinks = Arrays.asList(
            createAssetLink(1L, 1L, "GRID_A")
        );

        AnalyticsResult expectedResult = new AnalyticsResult();
        expectedResult.status = "SUCCESS";
        expectedResult.recordsProcessed = 0;
        expectedResult.timestamp = LocalDateTime.now();

        Mockito.when(analyticsService.calculateMetricsFromEvents(Arrays.asList(telemetry), assetLinks))
               .thenReturn(Uni.createFrom().item(expectedResult));

        AnalyticsRequest request = new AnalyticsRequest(Arrays.asList(telemetry), assetLinks);

        Response response = resource.evaluate(request).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        Mockito.verify(analyticsService, Mockito.times(1)).calculateMetricsFromEvents(Arrays.asList(telemetry), assetLinks);
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

    private Row dischargedZoneRow(Long id, String gridCellId, Double totalEnergyDischargedKw,
                                  Integer batteryCount, LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("gridCellId")).thenReturn(gridCellId);
        Mockito.when(row.getDouble("totalEnergyDischargedKw")).thenReturn(totalEnergyDischargedKw);
        Mockito.when(row.getInteger("batteryCount")).thenReturn(batteryCount);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("aggregationPeriod")).thenReturn(aggregationPeriod);
        return row;
    }

    private Row generatedProsumerRow(Long id, Long prosumerId, Double totalEnergyGeneratedKw,
                                     Integer solarAssetCount, LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getDouble("totalEnergyGeneratedKw")).thenReturn(totalEnergyGeneratedKw);
        Mockito.when(row.getInteger("solarAssetCount")).thenReturn(solarAssetCount);
        Mockito.when(row.getLocalDateTime("timestamp")).thenReturn(timestamp);
        Mockito.when(row.getString("aggregationPeriod")).thenReturn(aggregationPeriod);
        return row;
    }

    private Row consumedProsumerRow(Long id, Long prosumerId, Double totalEnergyConsumedKw,
                                    Integer evChargerCount, LocalDateTime timestamp, String aggregationPeriod) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getDouble("totalEnergyConsumedKw")).thenReturn(totalEnergyConsumedKw);
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

    private AssetLinkDTO createAssetLink(Long assetId, Long prosumerId, String gridCellId) {
        AssetLinkDTO dto = new AssetLinkDTO();
        dto.assetId = assetId;
        dto.prosumerId = prosumerId;
        dto.gridCellId = gridCellId;
        dto.status = "ACTIVE";
        return dto;
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
