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
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;

class AssetLinkResourceTest {

    AssetLinkResource resource;
    private MySQLPool client;
    private Emitter<String> assetLinkEventsEmitter;

    @BeforeEach
    void setup() {
        resource = new AssetLinkResource();
        client = Mockito.mock(MySQLPool.class);
        assetLinkEventsEmitter = Mockito.mock(Emitter.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
        injectEmitter(resource, assetLinkEventsEmitter);
    }

    @Test
    void getAssetLinks_returnsList() {
        Row row1 = assetLinkRow(1L, 1L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 2L, 1L, 1L, "LISBON-DT", "ACTIVE");
        stubQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink ORDER BY assetLinkId ASC", rowSetWithRows(row1, row2));

        List<AssetLink> result = resource.get().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).assetLinkId, is(1L));
        MatcherAssert.assertThat(result.get(0).assetId, is(1L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).utilityOperatorId, is(1L));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("LISBON-DT"));
        MatcherAssert.assertThat(result.get(0).status, is("ACTIVE"));
    }

    @Test
    void getAssetLinkById_returnsEntity() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));

        Response response = resource.getSingle(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        AssetLink entity = (AssetLink) response.getEntity();
        MatcherAssert.assertThat(entity, notNullValue());
        MatcherAssert.assertThat(entity.assetLinkId, is(1L));
        MatcherAssert.assertThat(entity.assetId, is(5L));
        MatcherAssert.assertThat(entity.prosumerId, is(2L));
        MatcherAssert.assertThat(entity.utilityOperatorId, is(1L));
        MatcherAssert.assertThat(entity.gridCellId, is("FARO-DT"));
        MatcherAssert.assertThat(entity.status, is("ACTIVE"));
    }

    @Test
    void getAssetLinkById_returnsNotFound() {
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows());

        Response response = resource.getSingle(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getAssetLinksByAssetId_returnsList() {
        Row row1 = assetLinkRow(1L, 5L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 5L, 2L, 2L, "PORTO-IN", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetId = ?", rowSetWithRows(row1, row2));

        List<AssetLink> result = resource.getByAssetId(5L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).assetId, is(5L));
        MatcherAssert.assertThat(result.get(1).assetId, is(5L));
    }

    @Test
    void getAssetLinksByGridCellId_returnsList() {
        Row row1 = assetLinkRow(1L, 1L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 2L, 1L, 1L, "LISBON-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE gridCellId = ?", rowSetWithRows(row1, row2));

        List<AssetLink> result = resource.getByGridCellId("LISBON-DT").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).gridCellId, is("LISBON-DT"));
        MatcherAssert.assertThat(result.get(1).gridCellId, is("LISBON-DT"));
    }

    @Test
    void getAssetLinksByStatus_returnsActiveOnly() {
        Row row1 = assetLinkRow(1L, 1L, 1L, 1L, "LISBON-DT", "ACTIVE");
        Row row2 = assetLinkRow(2L, 2L, 1L, 1L, "PORTO-IN", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE status = ?", rowSetWithRows(row1, row2));

        List<AssetLink> result = resource.getByStatus("ACTIVE").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).status, is("ACTIVE"));
        MatcherAssert.assertThat(result.get(1).status, is("ACTIVE"));
    }

    @Test
    void getAssetLinksByStatus_returnsEmptyList() {
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE status = ?", rowSetWithRows());

        List<AssetLink> result = resource.getByStatus("INACTIVE").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    @Test
    void createAssetLink_returnsCreated() {
        AssetLink assetLink = new AssetLink(null, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("INSERT INTO AssetLink(assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (?,?,?,?,?)", rowSetWithRowCount(1));

        Response response = resource.create(assetLink).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));

        Mockito.verify(assetLinkEventsEmitter).send(any(Message.class));
    }

    @Test
    void updateAssetLink_returnsNoContent() {
        AssetLink assetLink = new AssetLink(null, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("UPDATE AssetLink SET assetId = ?, prosumerId = ?, utilityOperatorId = ?, gridCellId = ?, status = ? WHERE assetLinkId = ?", rowSetWithRowCount(1));

        Response response = resource.update(1L, assetLink).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));

        Mockito.verify(assetLinkEventsEmitter).send(any(Message.class));
    }

    @Test
    void updateAssetLink_returnsNotFound() {
        AssetLink assetLink = new AssetLink(null, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("UPDATE AssetLink SET assetId = ?, prosumerId = ?, utilityOperatorId = ?, gridCellId = ?, status = ? WHERE assetLinkId = ?", rowSetWithRowCount(0));

        Response response = resource.update(99L, assetLink).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));

        Mockito.verify(assetLinkEventsEmitter, Mockito.never()).send(any(Message.class));
    }

    @Test
    void updateAssetLinkStatus_returnsNoContent() {
        stubPreparedQuery("UPDATE AssetLink SET status = ? WHERE assetLinkId = ?", rowSetWithRowCount(1));
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "INACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));

        Response response = resource.updateStatus(1L, "INACTIVE").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));

        Mockito.verify(assetLinkEventsEmitter).send(any(Message.class));
    }

    @Test
    void updateAssetLinkStatus_returnsNotFound() {
        stubPreparedQuery("UPDATE AssetLink SET status = ? WHERE assetLinkId = ?", rowSetWithRowCount(0));

        Response response = resource.updateStatus(99L, "INACTIVE").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));

        Mockito.verify(assetLinkEventsEmitter, Mockito.never()).send(any(Message.class));
    }

    @Test
    void deleteAssetLink_returnsNoContent() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?", rowSetWithRowCount(1));

        Response response = resource.delete(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));

        Mockito.verify(assetLinkEventsEmitter).send(any(Message.class));
    }

    @Test
    void deleteAssetLink_returnsNotFound() {
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows());

        Response response = resource.delete(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));

        Mockito.verify(assetLinkEventsEmitter, Mockito.never()).send(any(Message.class));
    }

    @Test
    void deleteAssetLink_notFoundAfterFind() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?", rowSetWithRowCount(0));

        Response response = resource.delete(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));

        Mockito.verify(assetLinkEventsEmitter, Mockito.never()).send(any(Message.class));
    }

    @Test
    void deleteAssetLink_publishesMinimalEvent() {
        Row row = assetLinkRow(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");
        stubPreparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?", rowSetWithRows(row));
        stubPreparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?", rowSetWithRowCount(1));

        resource.delete(1L).await().indefinitely();

        ArgumentCaptor<Message<String>> messageCaptor = ArgumentCaptor.forClass(Message.class);
        Mockito.verify(assetLinkEventsEmitter).send(messageCaptor.capture());

        String payload = messageCaptor.getValue().getPayload();
        MatcherAssert.assertThat(payload.contains("\"assetLinkId\":1"), is(true));
        MatcherAssert.assertThat(payload.contains("\"eventType\":\"DELETED\""), is(true));
        MatcherAssert.assertThat(payload.contains("\"prosumerId\""), is(false));
        MatcherAssert.assertThat(payload.contains("\"assetId\""), is(false));
    }

    @Test
    void initdb_createsTableAndSeedData() {
        injectSchemaCreate(resource, true);

        RowSet<Row> mockRowSet = Mockito.mock(RowSet.class);
        Mockito.when(mockRowSet.rowCount()).thenReturn(0);

        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(mockRowSet));
        Mockito.when(client.query(Mockito.anyString())).thenReturn(query);

        resource.config(null);

        Mockito.verify(client, Mockito.atLeast(6)).query(Mockito.anyString());
    }

    private void injectClient(AssetLinkResource target, MySQLPool pool) {
        try {
            Field field = AssetLinkResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(AssetLinkResource target, boolean value) {
        try {
            Field field = AssetLinkResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
    }

    private void injectEmitter(AssetLinkResource target, Emitter<String> emitter) {
        try {
            Field field = AssetLinkResource.class.getDeclaredField("assetLinkEventsEmitter");
            field.setAccessible(true);
            field.set(target, emitter);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject assetLinkEventsEmitter", e);
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
