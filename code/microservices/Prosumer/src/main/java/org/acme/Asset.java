package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class Asset {

	public Long assetId;
	public Long prosumerId;
	public String assetType;
	public String model;
	public String status;

	public Asset() {
	}

	public Asset(Long assetId, Long prosumerId, String assetType, String model, String status) {
		this.assetId = assetId;
		this.prosumerId = prosumerId;
		this.assetType = assetType;
		this.model = model;
		this.status = status;
	}

	@Override
	public String toString() {
		return "{assetId:" + assetId + ", prosumerId:" + prosumerId + ", assetType:" + assetType +
				", model:" + model + ", status:" + status + "}\n";
	}

	private static Asset from(Row row) {
		return new Asset(row.getLong("assetId"), row.getLong("prosumerId"),
				row.getString("assetType"), row.getString("model"), row.getString("status"));
	}

	public static Multi<Asset> findAll(MySQLPool client) {
		return client.query("SELECT assetId, prosumerId, assetType, model, status FROM Asset ORDER BY assetId ASC").execute()
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(Asset::from);
	}

	public static Uni<Asset> findByAssetId(MySQLPool client, Long assetId) {
		return client.preparedQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE assetId = ?").execute(Tuple.of(assetId))
				.onItem().transform(RowSet::iterator)
				.onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
	}

	public static Multi<Asset> findByProsumerId(MySQLPool client, Long prosumerId) {
		return client.preparedQuery("SELECT assetId, prosumerId, assetType, model, status FROM Asset WHERE prosumerId = ?").execute(Tuple.of(prosumerId))
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(Asset::from);
	}

	public Uni<Boolean> save(MySQLPool client, Long assetId_R, Long prosumerId_R, String assetType_R, String model_R, String status_R) {
		return client.preparedQuery("INSERT INTO Asset(assetId, prosumerId, assetType, model, status) VALUES (?,?,?,?,?)").execute(Tuple.of(assetId_R, prosumerId_R, assetType_R, model_R, status_R))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}

	public static Uni<Boolean> delete(MySQLPool client, Long assetId_R) {
		return client.preparedQuery("DELETE FROM Asset WHERE assetId = ?").execute(Tuple.of(assetId_R))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}

	public static Uni<Boolean> updateStatus(MySQLPool client, Long assetId_R, String status_R) {
		return client.preparedQuery("UPDATE Asset SET status = ? WHERE assetId = ?").execute(Tuple.of(status_R, assetId_R))
				.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	}
}
