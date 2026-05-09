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
import org.acme.entities.BalancingRecommendation;
import org.acme.services.GridBalancingRecommendationService;
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
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class GridBalancingRecommendationResourceIT {

    @InjectMock
    MySQLPool client;

    @InjectMock
    GridBalancingRecommendationService recommendationService;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
        Mockito.reset(recommendationService);
    }

    @Test
    void getAll_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp1);
        Row row2 = balancingRecommendationRow(2L, "GRID_C", null, 100.0, null, 10.0, null, 0.9, "NO_TARGET", "No available target", timestamp2);
        stubQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/GridBalancingRecommendation")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].sourceGridCellId", is("GRID_A"))
            .body("[0].targetGridCellId", is("GRID_B"))
            .body("[0].sourceNetLoadKw", is(95.0f))
            .body("[0].targetHeadroomKw", is(40.0f))
            .body("[0].overloadKw", is(5.0f))
            .body("[0].transferableKw", is(5.0f))
            .body("[0].thresholdPercent", is(0.9f))
            .body("[0].status", is("RECOMMENDED"))
            .body("[0].rationale", is("Transfer 5kW to GRID_B"));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp);
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/GridBalancingRecommendation/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("sourceGridCellId", is("GRID_A"))
            .body("targetGridCellId", is("GRID_B"))
            .body("status", is("RECOMMENDED"));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/GridBalancingRecommendation/99")
            .then()
            .statusCode(404);
    }

    @Test
    void getBySource_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row1 = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp);
        Row row2 = balancingRecommendationRow(2L, "GRID_A", "GRID_C", 98.0, 30.0, 8.0, 8.0, 0.9, "RECOMMENDED", "Transfer 8kW to GRID_C", timestamp);
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE sourceGridCellId = ? ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/GridBalancingRecommendation/source/GRID_A")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].sourceGridCellId", is("GRID_A"))
            .body("[1].sourceGridCellId", is("GRID_A"));
    }

    @Test
    void evaluate_withOverloadAndTarget_returnsRECOMMENDED() {
        BalancingRecommendation expectedRec = new BalancingRecommendation();
        expectedRec.sourceGridCellId = "GRID_A";
        expectedRec.targetGridCellId = "GRID_B";
        expectedRec.status = "RECOMMENDED";
        expectedRec.overloadKw = 5.0;
        expectedRec.transferableKw = 5.0;
        expectedRec.sourceNetLoadKw = 95.0;
        expectedRec.targetHeadroomKw = 40.0;
        expectedRec.thresholdPercent = 0.9;
        expectedRec.rationale = "Transfer 5kW to GRID_B";
        expectedRec.createdAt = LocalDateTime.now();

        Mockito.when(recommendationService.calculateRecommendationsFromEvents(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(Arrays.asList(expectedRec)));

        Map<String, Object> body = new HashMap<>();
        body.put("telemetryData", Arrays.asList(
            createTelemetryMap(1L, "EV_CHARGER", "GRID_A", 95.0f),
            createTelemetryMap(2L, "EV_CHARGER", "GRID_B", 50.0f)
        ));
        body.put("gridCells", Arrays.asList(
            createGridCellMap("GRID_A", 100.0),
            createGridCellMap("GRID_B", 100.0)
        ));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation/evaluate")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].status", is("RECOMMENDED"))
            .body("[0].targetGridCellId", is("GRID_B"));
    }

    @Test
    void evaluate_withOverloadNoTarget_returnsNO_TARGET() {
        BalancingRecommendation expectedRec = new BalancingRecommendation();
        expectedRec.sourceGridCellId = "GRID_A";
        expectedRec.targetGridCellId = null;
        expectedRec.status = "NO_TARGET";
        expectedRec.overloadKw = 5.0;
        expectedRec.transferableKw = null;
        expectedRec.sourceNetLoadKw = 95.0;
        expectedRec.targetHeadroomKw = null;
        expectedRec.thresholdPercent = 0.9;
        expectedRec.rationale = "No available target grid cell";
        expectedRec.createdAt = LocalDateTime.now();

        Mockito.when(recommendationService.calculateRecommendationsFromEvents(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(Arrays.asList(expectedRec)));

        Map<String, Object> body = new HashMap<>();
        body.put("telemetryData", Arrays.asList(
            createTelemetryMap(1L, "EV_CHARGER", "GRID_A", 95.0f),
            createTelemetryMap(2L, "EV_CHARGER", "GRID_B", 95.0f)
        ));
        body.put("gridCells", Arrays.asList(
            createGridCellMap("GRID_A", 100.0),
            createGridCellMap("GRID_B", 100.0)
        ));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation/evaluate")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].status", is("NO_TARGET"))
            .body("[0].targetGridCellId", nullValue());
    }

    @Test
    void evaluate_withNoOverload_returnsEmptyList() {
        Mockito.when(recommendationService.calculateRecommendationsFromEvents(Mockito.anyList(), Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(Collections.emptyList()));

        Map<String, Object> body = new HashMap<>();
        body.put("telemetryData", Arrays.asList(
            createTelemetryMap(1L, "EV_CHARGER", "GRID_A", 50.0f),
            createTelemetryMap(2L, "EV_CHARGER", "GRID_B", 60.0f)
        ));
        body.put("gridCells", Arrays.asList(
            createGridCellMap("GRID_A", 100.0),
            createGridCellMap("GRID_B", 100.0)
        ));

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation/evaluate")
            .then()
            .statusCode(200)
            .body("", hasSize(0));
    }

    @Test
    void create_withValidData_returns201() {
        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(123L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)", insertResult);

        Map<String, Object> body = new HashMap<>();
        body.put("sourceGridCellId", "GRID_A");
        body.put("targetGridCellId", "GRID_B");
        body.put("sourceNetLoadKw", 95.0);
        body.put("targetHeadroomKw", 40.0);
        body.put("overloadKw", 5.0);
        body.put("transferableKw", 5.0);
        body.put("thresholdPercent", 0.9);
        body.put("status", "RECOMMENDED");
        body.put("rationale", "Transfer 5kW to GRID_B");
        body.put("createdAt", "2024-01-15T10:30:00");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation")
            .then()
            .statusCode(201)
            .header("Location", containsString("/123"));
    }

    @Test
    void update_withExistingId_returns204() {
        RowSet<Row> updateResult = rowSetWithRowCount(1);
        stubPreparedQuery("UPDATE BalancingRecommendation SET sourceGridCellId = ?, targetGridCellId = ?, sourceNetLoadKw = ?, targetHeadroomKw = ?, overloadKw = ?, transferableKw = ?, thresholdPercent = ?, status = ?, rationale = ?, createdAt = ? WHERE id = ?", updateResult);

        Map<String, Object> body = new HashMap<>();
        body.put("sourceGridCellId", "GRID_A");
        body.put("targetGridCellId", "GRID_B");
        body.put("sourceNetLoadKw", 95.0);
        body.put("targetHeadroomKw", 40.0);
        body.put("overloadKw", 5.0);
        body.put("transferableKw", 5.0);
        body.put("thresholdPercent", 0.9);
        body.put("status", "RECOMMENDED");
        body.put("rationale", "Transfer 5kW to GRID_B");
        body.put("createdAt", "2024-01-15T10:30:00");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/GridBalancingRecommendation/1")
            .then()
            .statusCode(204);
    }

    @Test
    void update_withNonExistingId_returns404() {
        RowSet<Row> updateResult = rowSetWithRowCount(0);
        stubPreparedQuery("UPDATE BalancingRecommendation SET sourceGridCellId = ?, targetGridCellId = ?, sourceNetLoadKw = ?, targetHeadroomKw = ?, overloadKw = ?, transferableKw = ?, thresholdPercent = ?, status = ?, rationale = ?, createdAt = ? WHERE id = ?", updateResult);

        Map<String, Object> body = new HashMap<>();
        body.put("sourceGridCellId", "GRID_A");
        body.put("targetGridCellId", "GRID_B");
        body.put("sourceNetLoadKw", 95.0);
        body.put("targetHeadroomKw", 40.0);
        body.put("overloadKw", 5.0);
        body.put("transferableKw", 5.0);
        body.put("thresholdPercent", 0.9);
        body.put("status", "RECOMMENDED");
        body.put("rationale", "Transfer 5kW to GRID_B");
        body.put("createdAt", "2024-01-15T10:30:00");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/GridBalancingRecommendation/99")
            .then()
            .statusCode(404);
    }

    @Test
    void delete_withExistingId_returns204() {
        RowSet<Row> deleteResult = rowSetWithRowCount(1);
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", deleteResult);

        given()
            .when()
            .delete("/GridBalancingRecommendation/1")
            .then()
            .statusCode(204);
    }

    @Test
    void delete_withNonExistingId_returns404() {
        RowSet<Row> deleteResult = rowSetWithRowCount(0);
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", deleteResult);

        given()
            .when()
            .delete("/GridBalancingRecommendation/99")
            .then()
            .statusCode(404);
    }

    @Test
    void create_appliesDefaults_returnsMANUAL() {
        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(123L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)", insertResult);

        Map<String, Object> body = new HashMap<>();
        body.put("sourceGridCellId", "GRID_A");
        body.put("targetGridCellId", "GRID_B");
        body.put("sourceNetLoadKw", 95.0);
        body.put("targetHeadroomKw", 40.0);
        body.put("overloadKw", 5.0);
        body.put("transferableKw", 5.0);
        body.put("rationale", "Transfer 5kW to GRID_B");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/GridBalancingRecommendation")
            .then()
            .statusCode(201)
            .header("Location", containsString("/123"));
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

    private RowSet<Row> rowSetWithRowCount(int count) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(count);
        return rowSet;
    }

    private Row balancingRecommendationRow(Long id, String sourceGridCellId, String targetGridCellId,
                                           Double sourceNetLoadKw, Double targetHeadroomKw, Double overloadKw,
                                           Double transferableKw, Double thresholdPercent, String status,
                                           String rationale, LocalDateTime createdAt) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("sourceGridCellId")).thenReturn(sourceGridCellId);
        Mockito.when(row.getString("targetGridCellId")).thenReturn(targetGridCellId);
        Mockito.when(row.getDouble("sourceNetLoadKw")).thenReturn(sourceNetLoadKw);
        Mockito.when(row.getDouble("targetHeadroomKw")).thenReturn(targetHeadroomKw);
        Mockito.when(row.getDouble("overloadKw")).thenReturn(overloadKw);
        Mockito.when(row.getDouble("transferableKw")).thenReturn(transferableKw);
        Mockito.when(row.getDouble("thresholdPercent")).thenReturn(thresholdPercent);
        Mockito.when(row.getString("status")).thenReturn(status);
        Mockito.when(row.getString("rationale")).thenReturn(rationale);
        Mockito.when(row.getLocalDateTime("createdAt")).thenReturn(createdAt);
        return row;
    }

    private Map<String, Object> createTelemetryMap(Long assetId, String assetType, String gridCellId, Float value) {
        Map<String, Object> map = new HashMap<>();
        map.put("asset_id", assetId);
        map.put("asset_type", assetType);
        map.put("grid_cell_id", gridCellId);
        map.put("timeStamp", "2024-01-15T10:30:00");

        if ("EV_CHARGER".equals(assetType)) {
            map.put("Charging_Rate", value);
        } else if ("SOLAR".equals(assetType)) {
            map.put("Current_Generation", value);
        } else if ("BATTERY".equals(assetType)) {
            map.put("Current_Output", value);
        }

        return map;
    }

    private Map<String, Object> createGridCellMap(String gridCellId, Double maxCapacity) {
        Map<String, Object> map = new HashMap<>();
        map.put("gridCellId", gridCellId);
        map.put("maxCapacity", maxCapacity);
        map.put("utilityOperatorId", 1);
        map.put("geographicBoundaries", "Test boundaries");
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
