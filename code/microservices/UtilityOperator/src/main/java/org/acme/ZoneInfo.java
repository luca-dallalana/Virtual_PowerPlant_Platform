package org.acme;

import io.smallrye.mutiny.Multi;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;

public class ZoneInfo {

    public String gridCellId;
    public String operatorName;
    public String operatorLocation;

    public ZoneInfo() {
    }

    public ZoneInfo(String gridCellId, String operatorName, String operatorLocation) {
        this.gridCellId = gridCellId;
        this.operatorName = operatorName;
        this.operatorLocation = operatorLocation;
    }

    private static ZoneInfo from(Row row) {
        return new ZoneInfo(
            row.getString("gridCellId"),
            row.getString("operatorName"),
            row.getString("operatorLocation")
        );
    }

    public static Multi<ZoneInfo> findAll(MySQLPool client) {
        return client.query(
            "SELECT gc.gridCellId, uo.name AS operatorName, uo.location AS operatorLocation " +
            "FROM GridCell gc " +
            "JOIN UtilityOperator uo ON gc.utilityOperatorId = uo.id " +
            "ORDER BY gc.gridCellId ASC"
        ).execute()
            .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
            .onItem().transform(ZoneInfo::from);
    }
}
