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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

class KafkaProvisioningResourceTest {

    private KafkaProvisioningResource resource;
    private MySQLPool client;
    @BeforeEach
    void setup() {
        resource = new KafkaProvisioningResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
    }

    @Test
    void get_returnsList() {
        Row row1 = telemetryRow(1L, LocalDateTime.of(2024, 1, 10, 12, 30), 1001L, "BATTERY", "CELL-1");
        Row row2 = telemetryRow(2L, LocalDateTime.of(2024, 1, 10, 12, 31), 1002L, "SOLAR", "CELL-2");
        stubQuery("SELECT *  FROM Telemetry ORDER BY id ASC", rowSetWithRows(row1, row2));

        List<Telemetry> result = resource.get().collect().asList().await().indefinitely();
        assertThat(result, hasSize(2));
        assertThat(result.get(0).id, is(1L));
        assertThat(result.get(0).asset_type, is("BATTERY"));
    }

    @Test
    void getSingle_returnsEntity() {
        Row row = telemetryRow(1L, LocalDateTime.of(2024, 1, 10, 12, 30), 1001L, "BATTERY", "CELL-1");
        stubPreparedQuery("SELECT * FROM Telemetry WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(1L).await().indefinitely();
        assertThat(response.getStatus(), is(200));
        Telemetry telemetry = (Telemetry) response.getEntity();
        assertThat(telemetry, notNullValue());
        assertThat(telemetry.asset_id, is(1001L));
    }

    @Test
    void getSingle_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM Telemetry WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(42L).await().indefinitely();
        assertThat(response.getStatus(), is(404));
    }

    @Test
    void getLatestByAssetId_returnsEntity() {
        Row row = telemetryRow(5L, LocalDateTime.of(2024, 1, 10, 12, 35), 1001L, "BATTERY", "CELL-1");
        stubPreparedQuery("SELECT * FROM Telemetry WHERE asset_id = ? AND timeStamp >= NOW() - INTERVAL 10 MINUTE ORDER BY timeStamp DESC LIMIT 1", rowSetWithRows(row));

        Response response = resource.getLatestByAssetId(1001L).await().indefinitely();
        assertThat(response.getStatus(), is(200));
        Telemetry telemetry = (Telemetry) response.getEntity();
        assertThat(telemetry, notNullValue());
        assertThat(telemetry.id, is(5L));
        assertThat(telemetry.asset_id, is(1001L));
        assertThat(telemetry.asset_type, is("BATTERY"));
    }

    @Test
    void getLatestByAssetId_returnsNotFound() {
        stubPreparedQuery("SELECT * FROM Telemetry WHERE asset_id = ? AND timeStamp >= NOW() - INTERVAL 10 MINUTE ORDER BY timeStamp DESC LIMIT 1", rowSetWithRows());

        Response response = resource.getLatestByAssetId(9999L).await().indefinitely();
        assertThat(response.getStatus(), is(404));
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

    private void injectClient(KafkaProvisioningResource target, MySQLPool pool) {
        try {
            Field field = KafkaProvisioningResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }


    private void injectSchemaCreate(KafkaProvisioningResource target, boolean value) {
        try {
            Field field = KafkaProvisioningResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
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
