package org.acme;

import io.vertx.mutiny.sqlclient.Row;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.containsString;

class AssetLinkTest {

    @Test
    void constructor_setsAllFields() {
        AssetLink assetLink = new AssetLink(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");

        MatcherAssert.assertThat(assetLink.assetLinkId, is(1L));
        MatcherAssert.assertThat(assetLink.assetId, is(5L));
        MatcherAssert.assertThat(assetLink.prosumerId, is(2L));
        MatcherAssert.assertThat(assetLink.utilityOperatorId, is(1L));
        MatcherAssert.assertThat(assetLink.gridCellId, is("FARO-DT"));
        MatcherAssert.assertThat(assetLink.status, is("ACTIVE"));
    }

    @Test
    void toString_containsAllFields() {
        AssetLink assetLink = new AssetLink(1L, 5L, 2L, 1L, "FARO-DT", "ACTIVE");

        String result = assetLink.toString();

        MatcherAssert.assertThat(result, notNullValue());
        MatcherAssert.assertThat(result, containsString("assetLinkId:1"));
        MatcherAssert.assertThat(result, containsString("assetId:5"));
        MatcherAssert.assertThat(result, containsString("prosumerId:2"));
        MatcherAssert.assertThat(result, containsString("utilityOperatorId:1"));
        MatcherAssert.assertThat(result, containsString("gridCellId:FARO-DT"));
        MatcherAssert.assertThat(result, containsString("status:ACTIVE"));
    }

    @Test
    void from_mapsRowToAssetLink() throws Exception {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("assetLinkId")).thenReturn(1L);
        Mockito.when(row.getLong("assetId")).thenReturn(5L);
        Mockito.when(row.getLong("prosumerId")).thenReturn(2L);
        Mockito.when(row.getLong("utilityOperatorId")).thenReturn(1L);
        Mockito.when(row.getString("gridCellId")).thenReturn("FARO-DT");
        Mockito.when(row.getString("status")).thenReturn("ACTIVE");

        Method fromMethod = AssetLink.class.getDeclaredMethod("from", Row.class);
        fromMethod.setAccessible(true);
        AssetLink assetLink = (AssetLink) fromMethod.invoke(null, row);

        MatcherAssert.assertThat(assetLink, notNullValue());
        MatcherAssert.assertThat(assetLink.assetLinkId, is(1L));
        MatcherAssert.assertThat(assetLink.assetId, is(5L));
        MatcherAssert.assertThat(assetLink.prosumerId, is(2L));
        MatcherAssert.assertThat(assetLink.utilityOperatorId, is(1L));
        MatcherAssert.assertThat(assetLink.gridCellId, is("FARO-DT"));
        MatcherAssert.assertThat(assetLink.status, is("ACTIVE"));
    }
}
