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
import org.acme.Asset;
import org.acme.AssetDTO;
import org.acme.Prosumer;
import org.acme.ProsumerResource;
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

class ProsumerResourceUnitTest {

    ProsumerResource resource;

    private MySQLPool client;

    @BeforeEach
    void setup() {
        resource = new ProsumerResource();
        client = Mockito.mock(MySQLPool.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
    }

    @Test
    void getProsumers_returnsList() {
        Row row1 = prosumerRow(1L, "client1", 123456L, "Lisbon");
        Row row2 = prosumerRow(2L, "client2", 987654L, "Setubal");
        stubQuery("SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC", rowSetWithRows(row1, row2));

        List<Prosumer> result = resource.get().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).name, is("client1"));
        MatcherAssert.assertThat(result.get(0).location, is("Lisbon"));
        MatcherAssert.assertThat(result.get(0).FiscalNumber, is(123456L));
    }

    @Test
    void getProsumerById_returnsEntity() {
        Row row = prosumerRow(1L, "client1", 123456L, "Lisbon");
        stubPreparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getSingle(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        Prosumer entity = (Prosumer) response.getEntity();
        MatcherAssert.assertThat(entity, notNullValue());
        MatcherAssert.assertThat(entity.id, is(1L));
        MatcherAssert.assertThat(entity.name, is("client1"));
        MatcherAssert.assertThat(entity.FiscalNumber, is(123456L));
    }

    @Test
    void getProsumerById_returnsNotFound() {
        stubPreparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?", rowSetWithRows());

        Response response = resource.getSingle(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void createProsumer_returnsCreated() {
        Prosumer prosumer = new Prosumer();
        prosumer.name = "NewProsumer";
        prosumer.FiscalNumber = 112233L;
        prosumer.location = "Faro";
        stubPreparedQuery("INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)", rowSetWithRowCount(1));

        Response response = resource.create(prosumer).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(response.getLocation(), notNullValue());
        MatcherAssert.assertThat(response.getLocation().getPath(), startsWith("/Prosumer/"));
    }

    @Test
    void deleteProsumer_returnsNoContent() {
        stubPreparedQuery("DELETE FROM Prosumer WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.delete(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void deleteProsumer_returnsNotFound() {
        stubPreparedQuery("DELETE FROM Prosumer WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.delete(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateProsumer_returnsNoContent() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(1));

        Response response = resource.update(1L, "Updated", 778899L, "Lisbon").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void updateProsumer_returnsNotFound() {
        stubPreparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?", rowSetWithRowCount(0));

        Response response = resource.update(99L, "Missing", 778899L, "Porto").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getAssets_returnsList() {
        Row row1 = assetRow(1001L, 1L, "BATTERY", "Tesla Powerwall 2", "ACTIVE");
        Row row2 = assetRow(1002L, 1L, "SOLAR", "SolarEdge SE7600H", "ACTIVE");
        stubPreparedQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?", rowSetWithRows(row1, row2));

        List<Asset> result = resource.getAssets(1L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).assetId, is(1001L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).assetType, is("BATTERY"));
    }

    @Test
    void getAssets_returnsEmptyList() {
        stubPreparedQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?", rowSetWithRows());

        List<Asset> result = resource.getAssets(1L).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    @Test
    void createAsset_returnsCreated() {
        Asset asset = new Asset(1001L, 1L, "BATTERY", "Tesla Powerwall 2", "ACTIVE");
        stubPreparedQuery("INSERT INTO Asset(assetId, prosumerId, assetType, model, status) VALUES (?,?,?,?,?)", rowSetWithRowCount(1));

        Response response = resource.createAsset(1L, asset).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(response.getLocation(), notNullValue());
        MatcherAssert.assertThat(response.getLocation().getPath(), is("/Prosumer/1/assets/1001"));
    }

    @Test
    void deleteAsset_returnsNoContent() {
        stubPreparedQuery("DELETE FROM Asset WHERE assetId = ?", rowSetWithRowCount(1));

        Response response = resource.deleteAsset(1L, 1001L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void deleteAsset_returnsNotFound() {
        stubPreparedQuery("DELETE FROM Asset WHERE assetId = ?", rowSetWithRowCount(0));

        Response response = resource.deleteAsset(1L, 1001L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void updateAssetStatus_returnsNoContent() {
        stubPreparedQuery("UPDATE Asset SET status = ? WHERE assetId = ?", rowSetWithRowCount(1));

        Response response = resource.updateAssetStatus(1L, 1001L, "MAINTENANCE").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void updateAssetStatus_returnsNotFound() {
        stubPreparedQuery("UPDATE Asset SET status = ? WHERE assetId = ?", rowSetWithRowCount(0));

        Response response = resource.updateAssetStatus(1L, 1001L, "MAINTENANCE").await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getAllAssets_returnsList() {
        Row row1 = assetRow(1001L, 1L, "BATTERY", "Tesla Powerwall 2", "ACTIVE");
        Row row2 = assetRow(1002L, 1L, "SOLAR", "SolarEdge SE7600H", "ACTIVE");
        Row row3 = assetRow(1003L, 2L, "EV_CHARGER", "ChargePoint Home Flex", "ACTIVE");
        stubQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset ORDER BY assetId ASC", rowSetWithRows(row1, row2, row3));

        List<Asset> result = resource.getAllAssets().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(3));
        MatcherAssert.assertThat(result.get(0).assetId, is(1001L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(1).assetId, is(1002L));
        MatcherAssert.assertThat(result.get(2).prosumerId, is(2L));
    }

    @Test
    void getAllAssets_returnsEmptyList() {
        stubQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset ORDER BY assetId ASC", rowSetWithRows());

        List<Asset> result = resource.getAllAssets().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    @Test
    void getActiveAssetsByType_returnsList() {
        Row row1 = activeAssetRow(1001L, 1L, "BATTERY");
        Row row2 = activeAssetRow(1004L, 3L, "BATTERY");
        stubPreparedQuery("SELECT assetId, prosumerId, assetType FROM Asset WHERE assetType = ? AND status = ?", rowSetWithRows(row1, row2));

        List<AssetDTO> result = resource.getActiveAssetsByType("BATTERY").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).assetId, is(1001L));
        MatcherAssert.assertThat(result.get(0).prosumerId, is(1L));
        MatcherAssert.assertThat(result.get(0).assetType, is("BATTERY"));
        MatcherAssert.assertThat(result.get(1).assetId, is(1004L));
        MatcherAssert.assertThat(result.get(1).prosumerId, is(3L));
    }

    @Test
    void getActiveAssetsByType_returnsEmptyList() {
        stubPreparedQuery("SELECT assetId, prosumerId, assetType FROM Asset WHERE assetType = ? AND status = ?", rowSetWithRows());

        List<AssetDTO> result = resource.getActiveAssetsByType("SOLAR").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }


    @Test
    void getActiveAssetIdsByProsumers_returnsList() {
        Row row1 = Mockito.mock(Row.class);
        Mockito.when(row1.getLong("assetId")).thenReturn(1001L);
        Row row2 = Mockito.mock(Row.class);
        Mockito.when(row2.getLong("assetId")).thenReturn(1002L);
        stubPreparedQuery(
            "SELECT assetId FROM Asset WHERE status = 'ACTIVE' AND prosumerId IN (?, ?)",
            rowSetWithRows(row1, row2));

        List<Long> result = resource.getActiveAssetIdsByProsumers(Arrays.asList(1L, 2L))
                .collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0), is(1001L));
        MatcherAssert.assertThat(result.get(1), is(1002L));
    }

    @Test
    void getActiveAssetIdsByProsumers_emptyInput_returnsEmpty() {
        List<Long> result = resource.getActiveAssetIdsByProsumers(Collections.emptyList())
                .collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    private void injectClient(ProsumerResource target, MySQLPool pool) {
        try {
            Field field = ProsumerResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(ProsumerResource target, boolean value) {
        try {
            Field field = ProsumerResource.class.getDeclaredField("schemaCreate");
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

    private Row prosumerRow(Long id, String name, Long fiscalNumber, String location) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("name")).thenReturn(name);
        Mockito.when(row.getString("location")).thenReturn(location);
        Mockito.when(row.getLong("FiscalNumber")).thenReturn(fiscalNumber);
        return row;
    }

    private Row activeAssetRow(Long assetId, Long prosumerId, String assetType) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("assetId")).thenReturn(assetId);
        Mockito.when(row.getLong("prosumerId")).thenReturn(prosumerId);
        Mockito.when(row.getString("assetType")).thenReturn(assetType);
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
