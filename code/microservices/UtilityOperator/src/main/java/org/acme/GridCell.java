package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class GridCell {

	public String gridCellId;
	public Long utilityOperatorId;
	public Double maxCapacity;
	public String geographicBoundaries;

	public GridCell() {
	}

	public GridCell(String gridCellId, Long utilityOperatorId, Double maxCapacity, String geographicBoundaries) {
		this.gridCellId = gridCellId;
		this.utilityOperatorId = utilityOperatorId;
		this.maxCapacity = maxCapacity;
		this.geographicBoundaries = geographicBoundaries;
	}

	@Override
	public String toString() {
		return "{gridCellId:" + gridCellId + ", utilityOperatorId:" + utilityOperatorId +
				", maxCapacity:" + maxCapacity + ", geographicBoundaries:" + geographicBoundaries + "}\n";
	}

	private static GridCell from(Row row) {
		return new GridCell(row.getString("gridCellId"), row.getLong("utilityOperatorId"),
				row.getDouble("maxCapacity"), row.getString("geographicBoundaries"));
	}

	public static Multi<GridCell> findAll(MySQLPool client) {
		return client.query("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell ORDER BY gridCellId ASC").execute()
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(GridCell::from);
	}

	public static Uni<GridCell> findByGridCellId(MySQLPool client, String gridCellId) {
		return client.preparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE gridCellId = ?").execute(Tuple.of(gridCellId))
				.onItem().transform(RowSet::iterator)
				.onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
	}

	public static Multi<GridCell> findByUtilityOperatorId(MySQLPool client, Long utilityOperatorId) {
		return client.preparedQuery("SELECT gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries FROM GridCell WHERE utilityOperatorId = ?").execute(Tuple.of(utilityOperatorId))
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(GridCell::from);
	}

	public Uni<Boolean> save(MySQLPool client, String gridCellId, Long utilityOperatorId, Double maxCapacity, String geographicBoundaries) {
		return client.preparedQuery("INSERT INTO GridCell(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries) VALUES (?,?,?,?)").execute(Tuple.of(gridCellId, utilityOperatorId, maxCapacity, geographicBoundaries))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}

	public static Uni<Boolean> delete(MySQLPool client, String gridCellId) {
		return client.preparedQuery("DELETE FROM GridCell WHERE gridCellId = ?").execute(Tuple.of(gridCellId))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}

	public static Uni<Boolean> update(MySQLPool client, String gridCellId, Long utilityOperatorId, Double maxCapacity, String geographicBoundaries) {
		return client.preparedQuery("UPDATE GridCell SET utilityOperatorId = ?, maxCapacity = ?, geographicBoundaries = ? WHERE gridCellId = ?").execute(Tuple.of(utilityOperatorId, maxCapacity, geographicBoundaries, gridCellId))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}

	public static Uni<Boolean> updateMaxCapacity(MySQLPool client, String gridCellId, Double maxCapacity) {
		return client.preparedQuery("UPDATE GridCell SET maxCapacity = ? WHERE gridCellId = ?").execute(Tuple.of(maxCapacity, gridCellId))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}
}
