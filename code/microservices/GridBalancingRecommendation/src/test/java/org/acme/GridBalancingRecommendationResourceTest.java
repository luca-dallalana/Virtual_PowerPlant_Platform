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
import org.acme.dto.BalancingRecommendationDTO;
import org.acme.dto.GridCellDTO;
import org.acme.dto.GridCellMetricsDTO;
import org.acme.dto.GridCellMetricsRequest;
import org.acme.dto.TelemetryDTO;
import org.acme.entities.BalancingRecommendation;
import org.acme.services.GridBalancingRecommendationService;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

class GridBalancingRecommendationResourceTest {

    GridBalancingRecommendationResource resource;
    private MySQLPool client;
    private GridBalancingRecommendationService recommendationService;

    @BeforeEach
    void setup() {
        resource = new GridBalancingRecommendationResource();
        client = Mockito.mock(MySQLPool.class);
        recommendationService = Mockito.mock(GridBalancingRecommendationService.class);
        injectField("client", client);
        injectField("schemaCreate", false);
        injectField("recommendationService", recommendationService);
    }

    // ── CRUD tests ──────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsList() {
        LocalDateTime ts1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime ts2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts1);
        Row row2 = recommendationRow(2L, 1001L, "DISCHARGE", "LISBON-DT", "PORTO-IN", ts2);
        stubQuery("SELECT id, assetId, action, fromCell, toCell, createdAt "
                + "FROM BalancingRecommendation ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        List<BalancingRecommendation> result = resource.getAll().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).assetId, is(1006L));
        MatcherAssert.assertThat(result.get(0).action, is("REDUCE_CHARGING"));
        MatcherAssert.assertThat(result.get(0).fromCell, is("PORTO-IN"));
        MatcherAssert.assertThat(result.get(0).toCell, is("PORTO-IN"));
        MatcherAssert.assertThat(result.get(1).action, is("DISCHARGE"));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts);
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt "
                + "FROM BalancingRecommendation WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getById(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        BalancingRecommendation result = (BalancingRecommendation) response.getEntity();
        MatcherAssert.assertThat(result.id, is(1L));
        MatcherAssert.assertThat(result.assetId, is(1006L));
        MatcherAssert.assertThat(result.action, is("REDUCE_CHARGING"));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt "
                + "FROM BalancingRecommendation WHERE id = ?", rowSetWithRows());

        Response response = resource.getById(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getBySource_returnsFiltered() {
        LocalDateTime ts = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row1 = recommendationRow(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts);
        Row row2 = recommendationRow(2L, 1007L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", ts);
        stubPreparedQuery("SELECT id, assetId, action, fromCell, toCell, createdAt "
                + "FROM BalancingRecommendation WHERE fromCell = ? ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        List<BalancingRecommendation> result = resource.getBySource("PORTO-IN").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).fromCell, is("PORTO-IN"));
        MatcherAssert.assertThat(result.get(1).fromCell, is("PORTO-IN"));
    }

    @Test
    void create_withValidData_returns201() {
        BalancingRecommendation rec = new BalancingRecommendation();
        rec.assetId = 1006L;
        rec.action = "REDUCE_CHARGING";
        rec.fromCell = "PORTO-IN";
        rec.toCell = "PORTO-IN";
        rec.createdAt = LocalDateTime.now();

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(42L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt) VALUES (?,?,?,?,?)", insertResult);

        Response response = resource.create(rec).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(response.getLocation().toString(), containsString("/42"));
    }

    @Test
    void create_setsCreatedAtWhenNull() {
        BalancingRecommendation rec = new BalancingRecommendation();
        rec.assetId = 1006L;
        rec.action = "REDUCE_CHARGING";
        rec.fromCell = "PORTO-IN";
        rec.toCell = "PORTO-IN";

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(42L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation (assetId, action, fromCell, toCell, createdAt) VALUES (?,?,?,?,?)", insertResult);

        resource.create(rec).await().indefinitely();
        MatcherAssert.assertThat(rec.createdAt, notNullValue());
    }

    @Test
    void update_withExistingId_returns204() {
        BalancingRecommendation rec = new BalancingRecommendation();
        rec.assetId = 1006L;
        rec.action = "REDUCE_CHARGING";
        rec.fromCell = "PORTO-IN";
        rec.toCell = "PORTO-IN";
        rec.createdAt = LocalDateTime.now();

        RowSet<Row> updateResult = rowSetWithRowCount(1);
        stubPreparedQuery("UPDATE BalancingRecommendation SET assetId = ?, action = ?, "
                + "fromCell = ?, toCell = ?, createdAt = ? WHERE id = ?", updateResult);

        Response response = resource.update(1L, rec).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void update_withNonExistingId_returns404() {
        BalancingRecommendation rec = new BalancingRecommendation();
        rec.assetId = 1006L;
        rec.action = "REDUCE_CHARGING";
        rec.fromCell = "PORTO-IN";
        rec.toCell = "PORTO-IN";
        rec.createdAt = LocalDateTime.now();

        RowSet<Row> updateResult = rowSetWithRowCount(0);
        stubPreparedQuery("UPDATE BalancingRecommendation SET assetId = ?, action = ?, "
                + "fromCell = ?, toCell = ?, createdAt = ? WHERE id = ?", updateResult);

        Response response = resource.update(99L, rec).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void delete_withExistingId_returns204() {
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.delete(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void delete_withNonExistingId_returns404() {
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.delete(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    // ── /metrics tests ───────────────────────────────────────────────────────────

    @Test
    void computeMetrics_singleCell_returnsMetrics() {
        GridCellDTO cell = new GridCellDTO();
        cell.gridCellId = "PORTO-IN";
        cell.maxCapacity = 75.0;

        TelemetryDTO ev = new TelemetryDTO();
        ev.asset_type = "EV_CHARGER";
        ev.grid_cell_id = "PORTO-IN";
        ev.Charging_Rate = 70.0f;

        GridCellMetricsDTO metrics = new GridCellMetricsDTO("PORTO-IN", 75.0, 70.0, -2.5);
        Mockito.when(recommendationService.computeSingleCellMetrics(
                Mockito.any(GridCellDTO.class), Mockito.anyList()))
               .thenReturn(metrics);

        GridCellMetricsRequest request = new GridCellMetricsRequest();
        request.gridCell = cell;
        request.telemetryData = List.of(ev);

        Response response = resource.computeMetrics(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridCellMetricsDTO result = (GridCellMetricsDTO) response.getEntity();
        MatcherAssert.assertThat(result.gridCellId, is("PORTO-IN"));
        MatcherAssert.assertThat(result.netLoad, is(70.0));
    }

    @Test
    void computeMetrics_multiCell_returnsList() {
        GridCellDTO cell = new GridCellDTO();
        cell.gridCellId = "LISBON-DT";
        cell.maxCapacity = 50.0;

        List<GridCellMetricsDTO> metrics = List.of(
                new GridCellMetricsDTO("LISBON-DT", 50.0, 0.0, 45.0)
        );
        Mockito.when(recommendationService.computeMultiCellMetrics(
                Mockito.anyList(), Mockito.anyList()))
               .thenReturn(metrics);

        GridCellMetricsRequest request = new GridCellMetricsRequest();
        request.neighbourCells = List.of(cell);
        request.allTelemetry = Collections.emptyList();

        Response response = resource.computeMetrics(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    @Test
    void computeMetrics_emptyRequest_returns400() {
        GridCellMetricsRequest request = new GridCellMetricsRequest();

        Response response = resource.computeMetrics(request);
        MatcherAssert.assertThat(response.getStatus(), is(400));
    }

    // ── /save tests ──────────────────────────────────────────────────────────────

    @Test
    void saveRecommendations_returnsSavedList() {
        BalancingRecommendationDTO dto = new BalancingRecommendationDTO();
        dto.assetId = 1006L;
        dto.action = "REDUCE_CHARGING";
        dto.from = "PORTO-IN";
        dto.to = "PORTO-IN";

        BalancingRecommendation saved = new BalancingRecommendation(1L, 1006L, "REDUCE_CHARGING", "PORTO-IN", "PORTO-IN", LocalDateTime.now());
        Mockito.when(recommendationService.saveRecommendations(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(List.of(saved)));

        Response response = resource.saveRecommendations(List.of(dto)).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void injectField(String name, Object value) {
        try {
            Field field = GridBalancingRecommendationResource.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(resource, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject field: " + name, e);
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

    private RowSet<Row> rowSetWithRowCount(int count) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(count);
        return rowSet;
    }

    private Row recommendationRow(Long id, Long assetId, String action,
                                  String fromCell, String toCell, LocalDateTime createdAt) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getString("action")).thenReturn(action);
        Mockito.when(row.getString("fromCell")).thenReturn(fromCell);
        Mockito.when(row.getString("toCell")).thenReturn(toCell);
        Mockito.when(row.getLocalDateTime("createdAt")).thenReturn(createdAt);
        return row;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        ListRowIterator(List<Row> rows) {
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
