package org.acm;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;
import org.acme.GridCell;
import org.acme.UtilityOperator;
import org.acme.UtilityOperatorResource;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

class UtilityOperatorResourceUnitTest {

    UtilityOperatorResource resource;

    private MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new UtilityOperatorResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
    }

    @Test
    void getUtilityOperators_returnsList() {
        Row row1 = operatorRow(1L, "ArcoCegoLisbon", "Lisboa");
        Row row2 = operatorRow(2L, "PracadeBocage", "Setubal");
        stubQuery("SELECT id, name, location FROM UtilityOperator ORDER BY id ASC", rowSetWithRows(row1, row2));

        List<UtilityOperator> result = resource.get().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).name, is("ArcoCegoLisbon"));
        MatcherAssert.assertThat(result.get(0).location, is("Lisboa"));
    }

    @Test
    void getUtilityOperatorById_returnsEntity() {
        Row row = operatorRow(1L, "ArcoCegoLisbon", "Lisboa");
        stubPreparedQuery("SELECT id, name, location FROM UtilityOperator WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        UtilityOperator entity = (UtilityOperator) response.getEntity();
        MatcherAssert.assertThat(entity, notNullValue());
        MatcherAssert.assertThat(entity.id, is(1L));
        MatcherAssert.assertThat(entity.name, is("ArcoCegoLisbon"));
    }

    @Test
    void getUtilityOperatorById_returnsNotFound() {
        stubPreparedQuery("SELECT id, name, location FROM UtilityOperator WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void createUtilityOperator_returnsCreated() {
        UtilityOperator operator = new UtilityOperator();
        operator.name = "NewOperator";
        operator.location = "Faro";
        stubPreparedQuery("INSERT INTO UtilityOperator(name,location) VALUES (?,?)", rowSetWithRowCount(1));

        Response response = resource.create(operator).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(response.getLocation(), notNullValue());
        MatcherAssert.assertThat(response.getLocation().getPath(), startsWith("/UtilityOperator/"));
    }

    @Test
    void deleteUtilityOperator_returnsNoContent() {
        stubPreparedQuery("DELETE FROM UtilityOperator WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.delete(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void deleteUtilityOperator_returnsNotFound() {
        stubPreparedQuery("DELETE FROM UtilityOperator WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.delete(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateUtilityOperator_returnsNoContent() {
        stubPreparedQuery("UPDATE UtilityOperator SET name = ?, location = ? WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.update(1L, "Updated", "Lisboa").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void updateUtilityOperator_returnsNotFound() {
        stubPreparedQuery("UPDATE UtilityOperator SET name = ?, location = ? WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.update(99L, "Missing", "Porto").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getGridCells_returnsList() {
        Row row1 = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        Row row2 = gridCellRow("PORTO-IN", 3L, 75.0, "Porto Industrial Zone");
        stubQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell ORDER BY gridCellId ASC", rowSetWithRows(row1, row2));

        List<GridCell> result = resource.getAllGridCells().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("LISBON-DT"));
        MatcherAssert.assertThat(result.get(0).maxCapacity, is(50.0));
    }

    @Test
    void getGridCellById_returnsEntity() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows(row));

        Response response = resource.getGridCell("LISBON-DT").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridCell entity = (GridCell) response.getEntity();
        MatcherAssert.assertThat(entity, notNullValue());
        MatcherAssert.assertThat(entity.gridCellId, is("LISBON-DT"));
    }

    @Test
    void getGridCellById_returnsNotFound() {
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows());

        Response response = resource.getGridCell("UNKNOWN").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getGridCellsByOperator_returnsList() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?", rowSetWithRows(row));

        List<GridCell> result = resource.getGridCellsByOperator(1L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(1));
        MatcherAssert.assertThat(result.get(0).utilityOperatorId, is(1L));
    }

    @Test
    void getGridCellsByOperator_returnsEmptyList() {
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?", rowSetWithRows());

        List<GridCell> result = resource.getGridCellsByOperator(99L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    @Test
    void createGridCell_returnsCreated() {
        GridCell gridCell = new GridCell("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("INSERT INTO GridCell(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES (?,?,?,?)", rowSetWithRowCount(1));

        Response response = resource.createGridCell(gridCell).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(response.getLocation(), notNullValue());
        MatcherAssert.assertThat(response.getLocation().getPath(), is("/UtilityOperator/gridcells/LISBON-DT"));
    }

    @Test
    void deleteGridCell_returnsNoContent() {
        Row row = gridCellRow("LISBON-DT", 1L, 50.0, "Lisbon Downtown Area");
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM GridCell WHERE gridCellId = ?", rowSetWithRowCount(1));

        Response response = resource.deleteGridCell("LISBON-DT").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void deleteGridCell_returnsNotFound() {
        stubPreparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?", rowSetWithRows());
        stubPreparedQuery("DELETE FROM GridCell WHERE gridCellId = ?", rowSetWithRowCount(0));

        Response response = resource.deleteGridCell("UNKNOWN").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateGridCell_returnsNoContent() {
        GridCell updated = new GridCell("LISBON-DT", 1L, 60.0, "Updated");
        stubPreparedQuery("UPDATE GridCell SET utilityOperatorId = ?, maxCapacity = ?, geographicBoundaries = ? WHERE gridCellId = ?", rowSetWithRowCount(1));

        Response response = resource.updateGridCell("LISBON-DT", updated).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void updateGridCell_returnsNotFound() {
        GridCell updated = new GridCell("UNKNOWN", 1L, 60.0, "Updated");
        stubPreparedQuery("UPDATE GridCell SET utilityOperatorId = ?, maxCapacity = ?, geographicBoundaries = ? WHERE gridCellId = ?", rowSetWithRowCount(0));

        Response response = resource.updateGridCell("UNKNOWN", updated).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateGridCellCapacity_returnsNoContent() {
        stubPreparedQuery("UPDATE GridCell SET maxCapacity = ? WHERE gridCellId = ?", rowSetWithRowCount(1));

        Response response = resource.updateGridCellCapacity("LISBON-DT", 55.0).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void updateGridCellCapacity_returnsNotFound() {
        stubPreparedQuery("UPDATE GridCell SET maxCapacity = ? WHERE gridCellId = ?", rowSetWithRowCount(0));

        Response response = resource.updateGridCellCapacity("UNKNOWN", 55.0).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    private void injectClient(UtilityOperatorResource target, MySQLPool pool) {
        try {
            Field field = UtilityOperatorResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(UtilityOperatorResource target, boolean value) {
        try {
            Field field = UtilityOperatorResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
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

    private RowSet<Row> rowSetWithRowCount(int rowCount) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(rowCount);
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
