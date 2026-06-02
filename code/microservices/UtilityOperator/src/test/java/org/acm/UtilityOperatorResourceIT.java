package org.acm;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.endsWith;

@QuarkusTest
@SuppressWarnings({"deprecation", "unchecked"})
class UtilityOperatorResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @Test
    void getUtilityOperators_returnsList() {
        Row row1 = operatorRow(1L, "ArcoCegoLisbon", "Lisboa");
        Row row2 = operatorRow(2L, "PracadeBocage", "Setubal");
        stubQuery("SELECT id, name, location FROM UtilityOperator ORDER BY id ASC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/UtilityOperator")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].name", is("ArcoCegoLisbon"))
            .body("[0].location", is("Lisboa"));
    }

    @Test
    void getUtilityOperatorById_returnsEntity() {
        Row row = operatorRow(1L, "ArcoCegoLisbon", "Lisboa");
        stubPreparedQuery("SELECT id, name, location FROM UtilityOperator WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/UtilityOperator/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("name", is("ArcoCegoLisbon"))
            .body("location", is("Lisboa"));
    }

    @Test
    void getUtilityOperatorById_returnsNotFound() {
        stubPreparedQuery("SELECT id, name, location FROM UtilityOperator WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/UtilityOperator/99")
            .then()
            .statusCode(404);
    }

    @Test
    void createUtilityOperator_returnsCreated() {
        stubPreparedQuery("INSERT INTO UtilityOperator(name,location) VALUES (?,?)", rowSetWithRowCount(1, 42L));

        Map<String, Object> body = new HashMap<>();
        body.put("name", "NewOperator");
        body.put("location", "Faro");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/UtilityOperator")
            .then()
            .statusCode(201)
            .header("Location", endsWith("/UtilityOperator/42"));
    }

    @Test
    void deleteUtilityOperator_returnsNoContent() {
        stubPreparedQuery("DELETE FROM UtilityOperator WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/UtilityOperator/1")
            .then()
            .statusCode(204);
    }

    @Test
    void updateUtilityOperator_returnsNoContent() {
        stubPreparedQuery("UPDATE UtilityOperator SET name = ?, location = ? WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .put("/UtilityOperator/1/Updated/Lisboa")
            .then()
            .statusCode(204);
    }

    @Test
    void getGridCells_returnsList() {
        Row row1 = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        Row row2 = gridCellRow("PORTO-IN", 3L, 75.0, "Porto Industrial Zone");
        stubQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell ORDER BY gridCellId ASC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/UtilityOperator/gridcells")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].gridCellId", is("LISBON-DT"))
            .body("[0].maxCapacity", is(50.0f));
    }

    @Test
    void getGridCellById_returnsEntity() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/UtilityOperator/gridcells/LISBON-DT")
            .then()
            .statusCode(200)
            .body("gridCellId", is("LISBON-DT"))
            .body("utilityOperatorId", is(1))
            .body("maxCapacity", is(50.0f));
    }

    @Test
    void getGridCellsByOperator_returnsList() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/UtilityOperator/1/gridcells")
            .then()
            .statusCode(200)
            .body("", hasSize(1))
            .body("[0].utilityOperatorId", is(1));
    }

    @Test
    void getGridCellsByOperator_returnsEmptyList() {
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?", rowSetWithRows());

        given()
            .when()
            .get("/UtilityOperator/99/gridcells")
            .then()
            .statusCode(200)
            .body("", hasSize(0));
    }

    @Test
    void createGridCell_returnsCreated() {
        stubPreparedQuery("INSERT INTO GridCell(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES (?,?,?,?)", rowSetWithRowCount(1));

        Map<String, Object> body = new HashMap<>();
        body.put("gridCellId", "LISBON-DT");
        body.put("utilityOperatorId", 1L);
        body.put("maxCapacity", 50.0);
        body.put("geographicBoundaries", "Lisbon Downtown Area");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/UtilityOperator/gridcells")
            .then()
            .statusCode(201)
            .header("Location", endsWith("/UtilityOperator/gridcells/LISBON-DT"));
    }

    @Test
    void deleteGridCell_returnsNoContent() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM GridCell WHERE gridCellId = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/UtilityOperator/gridcells/LISBON-DT")
            .then()
            .statusCode(204);
    }

    @Test
    void deleteGridCell_returnsNotFound() {
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows());
        stubPreparedQuery("DELETE FROM GridCell WHERE gridCellId = ?", rowSetWithRowCount(0));

        given()
            .when()
            .delete("/UtilityOperator/gridcells/UNKNOWN")
            .then()
            .statusCode(404);
    }

    @Test
    void updateGridCell_returnsNoContent() {
        stubPreparedQuery("UPDATE GridCell SET utilityOperatorId = ?, maxCapacity = ?, geographicBoundaries = ? WHERE gridCellId = ?", rowSetWithRowCount(1));

        Map<String, Object> body = new HashMap<>();
        body.put("gridCellId", "LISBON-DT");
        body.put("utilityOperatorId", 1L);
        body.put("maxCapacity", 60.0);
        body.put("geographicBoundaries", "Updated");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/UtilityOperator/gridcells/LISBON-DT")
            .then()
            .statusCode(204);
    }

    @Test
    void updateGridCellCapacity_returnsNoContent() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("UPDATE GridCell SET maxCapacity = ? WHERE gridCellId = ?", rowSetWithRowCount(1));
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows(row));

        given()
            .when()
            .put("/UtilityOperator/gridcells/LISBON-DT/capacity/55.0")
            .then()
            .statusCode(204);
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

    private RowSet<Row> rowSetWithRowCount(int rowCount) {
        return rowSetWithRowCount(rowCount, null);
    }

    private RowSet<Row> rowSetWithRowCount(int rowCount, Long insertedId) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(rowCount);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(insertedId);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Collections.emptyList()));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private Row operatorRow(Long id, String name, String location) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("name")).thenReturn(name);
        Mockito.when(row.getString("location")).thenReturn(location);
        return row;
    }

    private Row gridCellRow(String gridCellId, Long utilityOperatorId, Double maxCapacity, String geographicBoundaries) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getString("gridCellId")).thenReturn(gridCellId);
        Mockito.when(row.getLong("utilityOperatorId")).thenReturn(utilityOperatorId);
        Mockito.when(row.getDouble("maxCapacity")).thenReturn(maxCapacity);
        Mockito.when(row.getString("geographicBoundaries")).thenReturn(geographicBoundaries);
        return row;
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
