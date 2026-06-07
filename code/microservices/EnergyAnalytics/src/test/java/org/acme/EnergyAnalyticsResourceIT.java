package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import org.acme.dto.AnalyticsResult;
import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;
import org.acme.services.AnalyticsCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class EnergyAnalyticsResourceIT {

    @InjectMock
    MySQLPool client;

    @InjectMock
    AnalyticsCalculationService analyticsService;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
        Mockito.reset(analyticsService);
    }

    @Test
    void getDischargedByZone_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = dischargedZoneRow(1L, "GRID_A", 100.5, 5, timestamp1, "CURRENT");
        Row row2 = dischargedZoneRow(2L, "GRID_B", 75.3, 3, timestamp2, "CURRENT");
        stubQuery("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/EnergyAnalytics/discharged-by-zone")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].gridCellId", is("GRID_A"))
            .body("[0].totalEnergyDischargedKwh", is(100.5f))
            .body("[0].batteryCount", is(5));
    }

    @Test
    void getDischargedByGridCell_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = dischargedZoneRow(1L, "GRID_A", 100.5, 5, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, gridCellId, totalEnergyDischargedKwh, batteryCount, timestamp, aggregationPeriod FROM EnergyDischargedByZone WHERE gridCellId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

        given()
            .when()
            .get("/EnergyAnalytics/discharged-by-zone/GRID_A")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].gridCellId", is("GRID_A"));
    }

    @Test
    void getGeneratedByProsumer_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = generatedProsumerRow(1L, 1L, 50.5, 2, timestamp1, "CURRENT");
        Row row2 = generatedProsumerRow(2L, 2L, 75.3, 3, timestamp2, "CURRENT");
        stubQuery("SELECT id, prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/EnergyAnalytics/generated-by-prosumer")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].prosumerId", is(1))
            .body("[0].totalEnergyGeneratedKwh", is(50.5f))
            .body("[0].solarAssetCount", is(2));
    }

    @Test
    void getGeneratedByProsumerId_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = generatedProsumerRow(1L, 1L, 50.5, 2, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, prosumerId, totalEnergyGeneratedKwh, solarAssetCount, timestamp, aggregationPeriod FROM GeneratedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

        given()
            .when()
            .get("/EnergyAnalytics/generated-by-prosumer/1")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].prosumerId", is(1));
    }

    @Test
    void getConsumedByProsumer_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = consumedProsumerRow(1L, 1L, 25.5, 1, timestamp1, "CURRENT");
        Row row2 = consumedProsumerRow(2L, 2L, 35.3, 2, timestamp2, "CURRENT");
        stubQuery("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/EnergyAnalytics/consumed-by-prosumer")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].prosumerId", is(1))
            .body("[0].totalEnergyConsumedKwh", is(25.5f))
            .body("[0].evChargerCount", is(1));
    }

    @Test
    void getConsumedByProsumerId_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = consumedProsumerRow(1L, 2L, 25.5, 1, timestamp, "CURRENT");
        stubPreparedQuery("SELECT id, prosumerId, totalEnergyConsumedKwh, evChargerCount, timestamp, aggregationPeriod FROM ConsumedEnergyByProsumer WHERE prosumerId = ? ORDER BY timestamp DESC", rowSetWithRows(row));

        given()
            .when()
            .get("/EnergyAnalytics/consumed-by-prosumer/2")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].prosumerId", is(2));
    }

    @Test
    void getAverageSoC_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = averageSoCRow(1L, 75.5, 10, timestamp1, "CURRENT");
        Row row2 = averageSoCRow(2L, 80.3, 12, timestamp2, "CURRENT");
        stubQuery("SELECT id, averageSocPercent, batteryCount, timestamp, aggregationPeriod FROM AverageSoC ORDER BY timestamp DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/EnergyAnalytics/average-soc")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].averageSocPercent", is(75.5f))
            .body("[0].batteryCount", is(10));
    }

    @Test
    void computeGeneratedByProsumer_returnsResult() {
        GeneratedEnergyByProsumer expected = new GeneratedEnergyByProsumer(
            null, 1L, 100.0, 1, LocalDateTime.of(2024, 1, 15, 10, 30), "LAST_30_MIN");

        Mockito.when(analyticsService.computeGeneratedByProsumer(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(Collections.singletonList(expected));

        Map<String, Object> body = new HashMap<>();
        body.put("assets", Collections.singletonList(createAssetMap(2L, 1L)));
        body.put("telemetry", Collections.singletonList(createTelemetryMap(2L, "SOLAR", 100.0f, null)));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/compute/generated-by-prosumer")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].prosumerId", is(1))
            .body("[0].totalEnergyGeneratedKwh", is(100.0f));
    }

    @Test
    void computeConsumedByProsumer_returnsResult() {
        ConsumedEnergyByProsumer expected = new ConsumedEnergyByProsumer(
            null, 2L, 25.0, 1, LocalDateTime.of(2024, 1, 15, 10, 30), "LAST_30_MIN");

        Mockito.when(analyticsService.computeConsumedByProsumer(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(Collections.singletonList(expected));

        Map<String, Object> body = new HashMap<>();
        body.put("assets", Collections.singletonList(createAssetMap(3L, 2L)));
        body.put("telemetry", Collections.singletonList(createTelemetryMap(3L, "EV_CHARGER", 25.0f, null)));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/compute/consumed-by-prosumer")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].prosumerId", is(2))
            .body("[0].totalEnergyConsumedKwh", is(25.0f));
    }

    @Test
    void computeDischargedByZone_returnsResult() {
        EnergyDischargedByZone expected = new EnergyDischargedByZone(
            null, "GRID_A", 50.0, 1, LocalDateTime.of(2024, 1, 15, 10, 30), "LAST_30_MIN");

        Mockito.when(analyticsService.computeDischargedByZone(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(Collections.singletonList(expected));

        Map<String, Object> body = new HashMap<>();
        body.put("zones", Collections.singletonList(createZoneMap("GRID_A")));
        body.put("telemetry", Collections.singletonList(createTelemetryMap(1L, "BATTERY", 50.0f, 75.0f)));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/compute/discharged-by-zone")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].gridCellId", is("GRID_A"))
            .body("[0].totalEnergyDischargedKwh", is(50.0f));
    }

    @Test
    void computeAverageSoC_returnsResult() {
        AverageSoC expected = new AverageSoC(
            null, 75.0, 1, LocalDateTime.of(2024, 1, 15, 10, 30), "LAST_30_MIN");

        Mockito.when(analyticsService.computeAverageSoC(Mockito.anyList())).thenReturn(expected);

        Map<String, Object> body = new HashMap<>();
        body.put("telemetry", Collections.singletonList(createTelemetryMap(1L, "BATTERY", 50.0f, 75.0f)));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/compute/average-soc")
            .then()
            .statusCode(200)
            .body("averageSocPercent", is(75.0f))
            .body("batteryCount", is(1));
    }

    @Test
    void persistConsumed_returnsSuccess() {
        AnalyticsResult expectedResult = new AnalyticsResult("SUCCESS", LocalDateTime.now(), 1);
        Mockito.when(analyticsService.persistConsumed(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        Map<String, Object> body = new HashMap<>();
        body.put("consumedByProsumer", Collections.emptyList());

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/persist/consume")
            .then()
            .statusCode(200)
            .body("status", is("SUCCESS"))
            .body("recordsProcessed", is(1));
    }

    @Test
    void persistGenerated_returnsSuccess() {
        AnalyticsResult expectedResult = new AnalyticsResult("SUCCESS", LocalDateTime.now(), 1);
        Mockito.when(analyticsService.persistGenerated(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        Map<String, Object> body = new HashMap<>();
        body.put("generatedByProsumer", Collections.emptyList());

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/persist/generate")
            .then()
            .statusCode(200)
            .body("status", is("SUCCESS"))
            .body("recordsProcessed", is(1));
    }

    @Test
    void persistDischarged_returnsSuccess() {
        AnalyticsResult expectedResult = new AnalyticsResult("SUCCESS", LocalDateTime.now(), 1);
        Mockito.when(analyticsService.persistDischarged(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        Map<String, Object> body = new HashMap<>();
        body.put("dischargedByZone", Collections.emptyList());

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/persist/discharge")
            .then()
            .statusCode(200)
            .body("status", is("SUCCESS"))
            .body("recordsProcessed", is(1));
    }

    @Test
    void persistAverage_returnsSuccess() {
        AnalyticsResult expectedResult = new AnalyticsResult("SUCCESS", LocalDateTime.now(), 1);
        Mockito.when(analyticsService.persistAverageSoC(Mockito.any()))
               .thenReturn(Uni.createFrom().item(expectedResult));

        Map<String, Object> body = new HashMap<>();
        body.put("averageSoC", createAverageSoCMap(80.0, 5));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/EnergyAnalytics/persist/average")
            .then()
            .statusCode(200)
            .body("status", is("SUCCESS"))
            .body("recordsProcessed", is(1));
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

    private Map<String, Object> createTelemetryMap(Long assetId, String assetType, Float value1, Float value2) {
        Map<String, Object> map = new HashMap<>();
        map.put("asset_id", assetId);
        map.put("asset_type", assetType);
        map.put("timeStamp", "2024-01-15T10:30:00");

        if ("BATTERY".equals(assetType)) {
            map.put("Current_Output", value1);
            map.put("State_of_Charge", value2);
        } else if ("SOLAR".equals(assetType)) {
            map.put("Current_Generation", value1);
        } else if ("EV_CHARGER".equals(assetType)) {
            map.put("Charging_Rate", value1);
        }

        return map;
    }

    private Map<String, Object> createAssetMap(Long assetId, Long prosumerId) {
        Map<String, Object> map = new HashMap<>();
        map.put("assetId", assetId);
        map.put("prosumerId", prosumerId);
        return map;
    }

    private Map<String, Object> createZoneMap(String gridCellId) {
        Map<String, Object> map = new HashMap<>();
        map.put("gridCellId", gridCellId);
        map.put("utilityOperatorId", 1);
        map.put("maxCapacity", 50.0);
        map.put("geographicBoundaries", "Test Area");
        return map;
    }

    private Map<String, Object> createAverageSoCMap(Double avgSoC, Integer batteryCount) {
        Map<String, Object> map = new HashMap<>();
        map.put("averageSocPercent", avgSoC);
        map.put("batteryCount", batteryCount);
        map.put("aggregationPeriod", "LAST_30_MIN");
        return map;
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
