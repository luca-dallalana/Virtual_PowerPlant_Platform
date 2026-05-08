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

@QuarkusTest
class AssetLinkResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @Test
    void getAssetLinks_returnsList() {
        Row row1 = assetLinkRow(1L, 1L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 2L, 1L, 1L, "LISBON-DT", "ACTIVE");
        stubQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink ORDER BY assetLinkId ASC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/AssetLink")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].assetLinkId", is(1))
            .body("[0].assetId", is(1))
            .body("[0].prosumerId", is(1))
            .body("[0].utilityOperatorId", is(1))
            .body("[0].gridCellId", is("LISBON-DT"))
            .body("[0].status", is("ACTIVE"));
    }

    @Test
    void getAssetLinkById_returnsEntity() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/AssetLink/1")
            .then()
            .statusCode(200)
            .body("assetLinkId", is(1))
            .body("assetId", is(5))
            .body("prosumerId", is(2))
            .body("utilityOperatorId", is(1))
            .body("gridCellId", is("FARO-DT"))
            .body("status", is("ACTIVE"));
    }

    @Test
    void getAssetLinkById_returnsNotFound() {
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows());

        given()
            .when()
            .get("/AssetLink/99")
            .then()
            .statusCode(404);
    }

    @Test
    void getAssetLinksByAssetId_returnsList() {
        Row row1 = assetLinkRow(1L, 5L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 5L, 2L, 2L, "PORTO-IN", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetId = ?", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/AssetLink/asset/5")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].assetId", is(5))
            .body("[1].assetId", is(5));
    }

    @Test
    void getAssetLinksByGridCellId_returnsList() {
        Row row1 = assetLinkRow(1L, 1L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 2L, 1L, 1L, "LISBON-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE gridCellId = ?", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/AssetLink/gridcell/LISBON-DT")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].gridCellId", is("LISBON-DT"))
            .body("[1].gridCellId", is("LISBON-DT"));
    }

    @Test
    void getAssetLinksByStatus_returnsActiveOnly() {
        Row row1 = assetLinkRow(1L, 1L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 2L, 1L, 1L, "PORTO-IN", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE status = ?", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/AssetLink/status/ACTIVE")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].status", is("ACTIVE"))
            .body("[1].status", is("ACTIVE"));
    }

    @Test
    void getAssetLinksByStatus_returnsEmptyList() {
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE status = ?", rowSetWithRows());

        given()
            .when()
            .get("/AssetLink/status/INACTIVE")
            .then()
            .statusCode(200)
            .body("", hasSize(0));
    }

    @Test
    void createAssetLink_returnsCreated() {
        stubPreparedQuery("INSERT INTO AssetLink(assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (?,?,?,?,?)", rowSetWithRowCount(1));

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 5);
        body.put("prosumerId", 2);
        body.put("utilityOperatorId", 1);
        body.put("gridCellId", "FARO-DT");
        body.put("status", "ACTIVE");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/AssetLink")
            .then()
            .statusCode(201);
    }

    @Test
    void updateAssetLink_returnsNoContent() {
        stubPreparedQuery("UPDATE AssetLink SET assetId = ?, prosumerId = ?, utilityOperatorId = ?, gridCellId = ?, status = ? WHERE assetLinkId = ?", rowSetWithRowCount(1));

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 5);
        body.put("prosumerId", 2);
        body.put("utilityOperatorId", 1);
        body.put("gridCellId", "FARO-DT");
        body.put("status", "ACTIVE");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/AssetLink/1")
            .then()
            .statusCode(204);
    }

    @Test
    void updateAssetLink_returnsNotFound() {
        stubPreparedQuery("UPDATE AssetLink SET assetId = ?, prosumerId = ?, utilityOperatorId = ?, gridCellId = ?, status = ? WHERE assetLinkId = ?", rowSetWithRowCount(0));

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 5);
        body.put("prosumerId", 2);
        body.put("utilityOperatorId", 1);
        body.put("gridCellId", "FARO-DT");
        body.put("status", "ACTIVE");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .put("/AssetLink/99")
            .then()
            .statusCode(404);
    }

    @Test
    void updateAssetLinkStatus_returnsNoContent() {
        stubPreparedQuery("UPDATE AssetLink SET status = ? WHERE assetLinkId = ?", rowSetWithRowCount(1));
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "INACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));

        given()
            .when()
            .put("/AssetLink/1/status/INACTIVE")
            .then()
            .statusCode(204);
    }

    @Test
    void updateAssetLinkStatus_returnsNotFound() {
        stubPreparedQuery("UPDATE AssetLink SET status = ? WHERE assetLinkId = ?", rowSetWithRowCount(0));

        given()
            .when()
            .put("/AssetLink/99/status/INACTIVE")
            .then()
            .statusCode(404);
    }

    @Test
    void deleteAssetLink_returnsNoContent() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/AssetLink/1")
            .then()
            .statusCode(204);
    }

    @Test
    void deleteAssetLink_returnsNotFound() {
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows());

        given()
            .when()
            .delete("/AssetLink/99")
            .then()
            .statusCode(404);
    }

    @Test
    void deleteAssetLink_notFoundAfterFind() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?", rowSetWithRowCount(0));

        given()
            .when()
            .delete("/AssetLink/1")
            .then()
            .statusCode(404);
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
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(rowCount);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Collections.emptyList()));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private Row assetLinkRow(Long assetLinkId, Long assetId, Long prosumerId, Long utilityOperatorId, String gridCellId, String status) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("assetLinkId")).thenReturn(assetLinkId);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getLong("utilityOperatorId")).thenReturn(utilityOperatorId);
        Mockito.when(row.getString("gridCellId")).thenReturn(gridCellId);
        Mockito.when(row.getString("status")).thenReturn(status);
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
