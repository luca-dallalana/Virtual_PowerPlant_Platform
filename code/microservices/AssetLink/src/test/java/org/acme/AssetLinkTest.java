package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;

class AssetLinkTest {

    @Test
    void constructor_setsAllFields() {
        AssetLink assetLink = new AssetLink(1L, 2L, 3L);

        MatcherAssert.assertThat(assetLink.id, is(1L));
        MatcherAssert.assertThat(assetLink.idProsumer, is(2L));
        MatcherAssert.assertThat(assetLink.idUtilityOperator, is(3L));
    }

    @Test
    void toString_containsAllFields() {
        AssetLink assetLink = new AssetLink(1L, 2L, 3L);

        String result = assetLink.toString();

        MatcherAssert.assertThat(result, notNullValue());
        MatcherAssert.assertThat(result, containsString("id:1"));
        MatcherAssert.assertThat(result, containsString("idProsumer:2"));
        MatcherAssert.assertThat(result, containsString("idUtilityOperator:3"));
    }

    @Test
    void from_mapsRowToAssetLink() throws Exception {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(1L);
        Mockito.when(row.getLong("idProsumer")).thenReturn(2L);
        Mockito.when(row.getLong("idUtilityOperator")).thenReturn(3L);

        Method fromMethod = AssetLink.class.getDeclaredMethod("from", Row.class);
        fromMethod.setAccessible(true);
        AssetLink assetLink = (AssetLink) fromMethod.invoke(null, row);

        MatcherAssert.assertThat(assetLink, notNullValue());
        MatcherAssert.assertThat(assetLink.id, is(1L));
        MatcherAssert.assertThat(assetLink.idProsumer, is(2L));
        MatcherAssert.assertThat(assetLink.idUtilityOperator, is(3L));
    }

    @Test
    void findAll_mapsRowsInOrder() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row1 = assetLinkRow(1L, 10L, 20L);
        Row row2 = assetLinkRow(2L, 11L, 21L);
        stubQuery(client, "SELECT id, idProsumer, idUtilityOperator  FROM AssetLink ORDER BY id ASC", rowSetWithRows(row1, row2));

        List<AssetLink> result = AssetLink.findAll(client).collect().asList().await().indefinitely();

        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).idProsumer, is(10L));
        MatcherAssert.assertThat(result.get(0).idUtilityOperator, is(20L));
        MatcherAssert.assertThat(result.get(1).id, is(2L));
    }

    @Test
    void findById_returnsAssetLinkWhenFound() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = assetLinkRow(7L, 8L, 9L);
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?", rowSetWithRows(row));

        AssetLink result = AssetLink.findById(client, 7L).await().indefinitely();

        MatcherAssert.assertThat(result, notNullValue());
        MatcherAssert.assertThat(result.id, is(7L));
        MatcherAssert.assertThat(result.idProsumer, is(8L));
        MatcherAssert.assertThat(result.idUtilityOperator, is(9L));
    }

    @Test
    void findById_returnsNullWhenNotFound() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?", rowSetWithRows());

        AssetLink result = AssetLink.findById(client, 99L).await().indefinitely();

        MatcherAssert.assertThat(result, is((AssetLink) null));
    }

    @Test
    void findById2_returnsAssetLinkWhenFound() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        Row row = assetLinkRow(4L, 5L, 6L);
        stubPreparedQuery(client, "SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idProsumer = ? AND idUtilityOperator = ?", rowSetWithRows(row));

        AssetLink result = AssetLink.findById2(client, 5L, 6L).await().indefinitely();

        MatcherAssert.assertThat(result, notNullValue());
        MatcherAssert.assertThat(result.id, is(4L));
        MatcherAssert.assertThat(result.idProsumer, is(5L));
        MatcherAssert.assertThat(result.idUtilityOperator, is(6L));
    }

    @Test
    void save_returnsGeneratedIdWhenInsertSucceeds() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        RowSet<Row> rowSet = rowSetWithRowCount(1);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
        stubPreparedQuery(client, "INSERT INTO AssetLink(idProsumer,idUtilityOperator) VALUES (?,?)", rowSet);

        Long result = new AssetLink().save(client, 5L, 6L).await().indefinitely();

        MatcherAssert.assertThat(result, is(42L));
    }

    @Test
    void save_returnsGeneratedIdWhenInsertSucceedsWithDifferentId() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        RowSet<Row> rowSet = rowSetWithRowCount(1);
        Mockito.when(rowSet.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)).thenReturn(99L);
        stubPreparedQuery(client, "INSERT INTO AssetLink(idProsumer,idUtilityOperator) VALUES (?,?)", rowSet);

        Long result = new AssetLink().save(client, 5L, 6L).await().indefinitely();

        MatcherAssert.assertThat(result, is(99L));
    }

    @Test
    void delete_returnsTrueWhenRowIsRemoved() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        stubPreparedQuery(client, "DELETE FROM AssetLink WHERE id = ?", rowSetWithRowCount(1));

        boolean result = AssetLink.delete(client, 7L).await().indefinitely();

        MatcherAssert.assertThat(result, is(true));
    }

    @Test
    void delete_returnsFalseWhenRowIsMissing() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        stubPreparedQuery(client, "DELETE FROM AssetLink WHERE id = ?", rowSetWithRowCount(0));

        boolean result = AssetLink.delete(client, 7L).await().indefinitely();

        MatcherAssert.assertThat(result, is(false));
    }

    @Test
    void update_returnsTrueWhenRowIsUpdated() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        stubPreparedQuery(client, "UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?", rowSetWithRowCount(1));

        boolean result = AssetLink.update(client, 7L, 8L, 9L).await().indefinitely();

        MatcherAssert.assertThat(result, is(true));
    }

    @Test
    void update_returnsFalseWhenRowIsMissing() {
        MySQLPool client = Mockito.mock(MySQLPool.class);
        stubPreparedQuery(client, "UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?", rowSetWithRowCount(0));

        boolean result = AssetLink.update(client, 7L, 8L, 9L).await().indefinitely();

        MatcherAssert.assertThat(result, is(false));
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
