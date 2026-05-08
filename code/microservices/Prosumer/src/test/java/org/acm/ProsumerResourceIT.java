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
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class ProsumerResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @Test
    void getProsumers_returnsList() {
        Row row1 = prosumerRow(1L, "client1", 123456L, "Lisbon");
        Row row2 = prosumerRow(2L, "client2", 987654L, "Setubal");
        stubQuery("SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/Prosumer")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].name", is("client1"))
            .body("[0].location", is("Lisbon"))
            .body("[0].FiscalNumber", is(123456));
    }

    @Test
    void getProsumerById_returnsEntity() {
        Row row = prosumerRow(1L, "client1", 123456L, "Lisbon");
        stubPreparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/Prosumer/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("name", is("client1"))
            .body("location", is("Lisbon"))
            .body("FiscalNumber", is(123456));
    }

    @Test
    void getProsumerById_returnsNotFound() {
        stubPreparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/Prosumer/99")
            .then()
            .statusCode(404);
    }

    @Test
    void createProsumer_returnsCreated() {
        stubPreparedQuery("INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)", rowSetWithRowCount(1));

        Map<String, Object> body = new HashMap<>();
        body.put("name", "NewProsumer");
        body.put("FiscalNumber", 112233L);
        body.put("location", "Faro");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/Prosumer")
            .then()
            .statusCode(201)
            .header("Location", startsWith("/Prosumer/"));
    }

    @Test
    void deleteProsumer_returnsNoContent() {
        stubPreparedQuery("DELETE FROM Prosumer WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/Prosumer/1")
            .then()
            .statusCode(204);
    }

    @Test
    void deleteProsumer_returnsNotFound() {
        stubPreparedQuery("DELETE FROM Prosumer WHERE id = ?", rowSetWithRowCount(0));

        given()
            .when()
            .delete("/Prosumer/99")
            .then()
            .statusCode(404);
    }

    @Test
    void updateProsumer_returnsNoContent() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .put("/Prosumer/1/Updated/778899/Lisbon")
            .then()
            .statusCode(204);
    }

    @Test
    void updateProsumer_returnsNotFound() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(0));

        given()
            .when()
            .put("/Prosumer/99/Missing/778899/Porto")
            .then()
            .statusCode(404);
    }

    @Test
    void getAssets_returnsList() {
        Row row1 = assetRow(1001L, 1L, "BATTERY", "Tesla Powerwall 2", "ACTIVE");
        Row row2 = assetRow(1002L, 1L, "SOLAR", "SolarEdge SE7600H", "ACTIVE");
        stubPreparedQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/Prosumer/1/assets")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].assetId", is(1001))
            .body("[0].prosumerId", is(1))
            .body("[0].assetType", is("BATTERY"));
    }

    @Test
    void getAssets_returnsEmptyList() {
        stubPreparedQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?", rowSetWithRows());

        given()
            .when()
            .get("/Prosumer/1/assets")
            .then()
            .statusCode(200)
            .body("", hasSize(0));
    }

    @Test
    void createAsset_returnsCreated() {
        stubPreparedQuery("INSERT INTO Asset(assetId, prosumerId, assetType, model, status) VALUES (?,?,?,?,?)", rowSetWithRowCount(1));

        Map<String, Object> body = new HashMap<>();
        body.put("assetId", 1001L);
        body.put("assetType", "BATTERY");
        body.put("model", "Tesla Powerwall 2");
        body.put("status", "ACTIVE");

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/Prosumer/1/assets")
            .then()
            .statusCode(201)
            .header("Location", is("/Prosumer/1/assets/1001"));
    }

    @Test
    void deleteAsset_returnsNoContent() {
        stubPreparedQuery("DELETE FROM Asset WHERE assetId = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/Prosumer/1/assets/1001")
            .then()
            .statusCode(204);
    }

    @Test
    void deleteAsset_returnsNotFound() {
        stubPreparedQuery("DELETE FROM Asset WHERE assetId = ?", rowSetWithRowCount(0));

        given()
            .when()
            .delete("/Prosumer/1/assets/1001")
            .then()
            .statusCode(404);
    }

    @Test
    void updateAssetStatus_returnsNoContent() {
        stubPreparedQuery("UPDATE Asset SET status = ? WHERE assetId = ?", rowSetWithRowCount(1));

        given()
            .when()
            .put("/Prosumer/1/assets/1001/status/MAINTENANCE")
            .then()
            .statusCode(204);
    }

    @Test
    void updateAssetStatus_returnsNotFound() {
        stubPreparedQuery("UPDATE Asset SET status = ? WHERE assetId = ?", rowSetWithRowCount(0));

        given()
            .when()
            .put("/Prosumer/1/assets/1001/status/MAINTENANCE")
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

    private Row prosumerRow(Long id, String name, Long fiscalNumber, String location) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("name")).thenReturn(name);
        Mockito.when(row.getString("location")).thenReturn(location);
        Mockito.when(row.getLong("FiscalNumber")).thenReturn(fiscalNumber);
        return row;
    }

    private Row assetRow(Long assetId, Long prosumerId, String assetType, String model, String status) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getString("assetType")).thenReturn(assetType);
        Mockito.when(row.getString("model")).thenReturn(model);
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
