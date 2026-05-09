package org.acme;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
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
import java.util.List;

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
    void get_returnsList() {
        Row row1 = assetLinkRow(1L, 2L, 3L);
        Row row2 = assetLinkRow(2L, 4L, 5L);
        stubQuery(client, "SELECT id, idProsumer, idUtilityOperator  FROM AssetLink ORDER BY id ASC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/AssetLink")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].idProsumer", is(2))
            .body("[0].idUtilityOperator", is(3))
            .body("[1].id", is(2))
            .body("[1].idProsumer", is(4))
            .body("[1].idUtilityOperator", is(5));
    }

    @Test
    void getSingle_returnsEntity() {
        Row row = assetLinkRow(1L, 2L, 3L);
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/AssetLink/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("idProsumer", is(2))
            .body("idUtilityOperator", is(3));
    }

    @Test
    void getSingle_returnsNotFound() {
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/AssetLink/99")
            .then()
            .statusCode(404);
    }

    @Test
    void getDual_returnsEntity() {
        Row row = assetLinkRow(1L, 2L, 3L);
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idProsumer = ? AND idUtilityOperator = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/AssetLink/2/3")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("idProsumer", is(2))
            .body("idUtilityOperator", is(3));
    }

    @Test
    void getDual_returnsNotFound() {
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idProsumer = ? AND idUtilityOperator = ?", rowSetWithRows());

        given()
            .when()
            .get("/AssetLink/8/9")
            .then()
            .statusCode(404);
    }

    @Test
    void create_returnsCreated() {
        stubPreparedQuery(client, "INSERT INTO AssetLink(idProsumer,idUtilityOperator) VALUES (?,?)", rowSetWithRowCount(1));

        AssetLink body = new AssetLink(null, 2L, 3L);

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/AssetLink")
            .then()
            .statusCode(201);
    }

    @Test
    void delete_returnsNoContent() {
        stubPreparedQuery(client, "DELETE FROM AssetLink WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .delete("/AssetLink/1")
            .then()
            .statusCode(204);
    }

    @Test
    void delete_returnsNotFound() {
        stubPreparedQuery(client, "DELETE FROM AssetLink WHERE id = ?", rowSetWithRowCount(0));

        given()
            .when()
            .delete("/AssetLink/99")
            .then()
            .statusCode(404);
    }

    @Test
    void update_returnsNoContent() {
        stubPreparedQuery(client, "UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?", rowSetWithRowCount(1));

        given()
            .when()
            .put("/AssetLink/1/2/3")
            .then()
            .statusCode(204);
    }

    @Test
    void update_returnsNotFound() {
        stubPreparedQuery(client, "UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?", rowSetWithRowCount(0));

        given()
            .when()
            .put("/AssetLink/99/2/3")
            .then()
            .statusCode(404);
    }

    private void stubQuery(MySQLPool client, String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(MySQLPool client, String sql, RowSet<Row> rowSet) {
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

    private Row assetLinkRow(Long id, Long idProsumer, Long idUtilityOperator) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("idProsumer")).thenReturn(idProsumer);
        Mockito.when(row.getLong("idUtilityOperator")).thenReturn(idUtilityOperator);
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
