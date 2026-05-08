package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class TelemetryRepositoryTest {

    @Test
    void findAll_mapsRows() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row1 = telemetryRow(1L, LocalDateTime.of(2024, 2, 1, 8, 0), 1001L, "BATTERY", "CELL-1");
        Row row2 = telemetryRow(2L, LocalDateTime.of(2024, 2, 1, 8, 5), 1002L, "SOLAR", "CELL-2");
        RowSet<Row> rowSet = rowSetWithRows(row1, row2);

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query("SELECT *  FROM Telemetry ORDER BY id ASC")).thenReturn(query);

        List<Telemetry> result = Telemetry.findAll(client).collect().asList().await().indefinitely();
        assertThat(result, hasSize(2));
        assertThat(result.get(0).asset_id, is(1001L));
        assertThat(result.get(1).asset_type, is("SOLAR"));
    }

    @Test
    void findById_returnsEntity() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = telemetryRow(3L, LocalDateTime.of(2024, 2, 1, 9, 0), 2001L, "EV_CHARGER", "CELL-3");
        RowSet<Row> rowSet = rowSetWithRows(row);

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("SELECT * FROM Telemetry WHERE id = ?")).thenReturn(preparedQuery);

        Telemetry result = Telemetry.findById(client, 3L).await().indefinitely();
        assertThat(result, notNullValue());
        assertThat(result.asset_type, is("EV_CHARGER"));
    }

    @Test
    void findById_returnsNullWhenMissing() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        RowSet<Row> rowSet = rowSetWithRows();

        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery("SELECT * FROM Telemetry WHERE id = ?")).thenReturn(preparedQuery);

        Telemetry result = Telemetry.findById(client, 99L).await().indefinitely();
        assertThat(result, is((Telemetry) null));
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
