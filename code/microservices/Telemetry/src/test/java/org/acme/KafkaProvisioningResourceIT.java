package org.acme;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;

import io.restassured.http.ContentType;

@QuarkusTest
class KafkaProvisioningResourceIT {

    @InjectMock
    MySQLPool client;

    @BeforeEach
    void setup() {
        Mockito.reset(client);
    }

    @Test
    void getTelemetry_returnsList() {
        Row row1 = telemetryRow(1L, LocalDateTime.of(2024, 1, 10, 12, 30), 1001L, "BATTERY", "CELL-1");
        Row row2 = telemetryRow(2L, LocalDateTime.of(2024, 1, 10, 12, 31), 1002L, "SOLAR", "CELL-2");
        stubQuery("SELECT *  FROM Telemetry ORDER BY id ASC", rowSetWithRows(row1, row2));

        given()
            .when()
            .get("/Telemetry")
            .then()
            .statusCode(200)
            .body("", hasSize(2))
            .body("[0].id", is(1))
            .body("[0].asset_type", is("BATTERY"));
    }

    @Test
    void getTelemetryById_returnsEntity() {
        Row row = telemetryRow(1L, LocalDateTime.of(2024, 1, 10, 12, 30), 1001L, "BATTERY", "CELL-1");
        stubPreparedQuery("SELECT * FROM Telemetry WHERE id = ?", rowSetWithRows(row));

        given()
            .when()
            .get("/Telemetry/1")
            .then()
            .statusCode(200)
            .body("id", is(1))
            .body("asset_id", is(1001))
            .body("asset_type", is("BATTERY"));
    }

    @Test
    void getTelemetryById_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM Telemetry WHERE id = ?", rowSetWithRows());

        given()
            .when()
            .get("/Telemetry/999")
            .then()
            .statusCode(404);
    }

    @Test
    void postConsumeEndpoint_returnsSuccessMessage() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"TopicName\":\"test-topic\"}")
            .when()
            .post("/Telemetry/Consume")
            .then()
            .statusCode(200)
            .body(is("New worker started"));
    }

    @Test
    void postConsumeEndpoint_withMalformedJson_returns4xx() {
        given()
            .contentType(ContentType.JSON)
            .body("{invalid json syntax")
            .when()
            .post("/Telemetry/Consume")
            .then()
            .statusCode(anyOf(is(400), is(500)));
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

    private Row telemetryRow(Long id, LocalDateTime timeStamp, Long assetId, String assetType, String gridCellId) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLocalDateTime("timeStamp")).thenReturn(timeStamp);
        Mockito.when(row.getLong("asset_id")).thenReturn(assetId);
        Mockito.when(row.getString("asset_type")).thenReturn(assetType);
        Mockito.when(row.getString("grid_cell_id")).thenReturn(gridCellId);
        Mockito.when(row.getFloat("State_of_Charge")).thenReturn(70.5f);
        Mockito.when(row.getFloat("Available_Energy")).thenReturn(12.3f);
        Mockito.when(row.getFloat("Current_Output")).thenReturn(3.2f);
        Mockito.when(row.getFloat("Max_Capacity")).thenReturn(5.0f);
        Mockito.when(row.getFloat("State_of_Health")).thenReturn(99.0f);
        Mockito.when(row.getString("Status")).thenReturn("CONNECTED");
        Mockito.when(row.getFloat("Current_Generation")).thenReturn(1.1f);
        Mockito.when(row.getFloat("Daily_Total")).thenReturn(6.8f);
        Mockito.when(row.getFloat("Grid_Voltage")).thenReturn(230.0f);
        Mockito.when(row.getFloat("Frequency")).thenReturn(50.0f);
        Mockito.when(row.getString("Plug_Status")).thenReturn("PLUGGED");
        Mockito.when(row.getFloat("Charging_Rate")).thenReturn(7.4f);
        Mockito.when(row.getFloat("Session_Energy")).thenReturn(2.2f);
        Mockito.when(row.getFloat("EV_SoC")).thenReturn(80.0f);
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
