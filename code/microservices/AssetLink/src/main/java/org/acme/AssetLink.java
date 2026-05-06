package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class AssetLink {

	    public Long assetLinkId;
		public Long assetId;
		public Long prosumerId;
		public Long utilityOperatorId;
		public String gridCellId;
		public String status;

	    public AssetLink() {
	    }


		public AssetLink(Long assetLinkId, Long assetId, Long prosumerId, Long utilityOperatorId, String gridCellId, String status) {
			this.assetLinkId = assetLinkId;
			this.assetId = assetId;
			this.prosumerId = prosumerId;
			this.utilityOperatorId = utilityOperatorId;
			this.gridCellId = gridCellId;
			this.status = status;
		}


		@Override
		public String toString() {
			return "{assetLinkId:" + assetLinkId + ", assetId:" + assetId + ", prosumerId:" + prosumerId +
				   ", utilityOperatorId:" + utilityOperatorId + ", gridCellId:" + gridCellId + ", status:" + status + "}\n";
		}

		private static AssetLink from(Row row) {
	        return new AssetLink(row.getLong("assetLinkId"), row.getLong("assetId"), row.getLong("prosumerId"),
								 row.getLong("utilityOperatorId"), row.getString("gridCellId"), row.getString("status"));
	    }
	    
	    public static Multi<AssetLink> findAll(MySQLPool client) {
	        return client.query("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink ORDER BY assetLinkId ASC").execute()
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(AssetLink::from);
	    }

	    public static Uni<AssetLink> findById(MySQLPool client, Long assetLinkId) {
	        return client.preparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetLinkId = ?").execute(Tuple.of(assetLinkId))
	                .onItem().transform(RowSet::iterator)
	                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null);
	    }

		public static Multi<AssetLink> findByAssetId(MySQLPool client, Long assetId) {
	        return client.preparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE assetId = ?").execute(Tuple.of(assetId))
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(AssetLink::from);
	    }

		public static Multi<AssetLink> findByGridCellId(MySQLPool client, String gridCellId) {
	        return client.preparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE gridCellId = ?").execute(Tuple.of(gridCellId))
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(AssetLink::from);
	    }

		public static Multi<AssetLink> findByStatus(MySQLPool client, String status) {
	        return client.preparedQuery("SELECT assetLinkId, assetId, prosumerId, utilityOperatorId, gridCellId, status FROM AssetLink WHERE status = ?").execute(Tuple.of(status))
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(AssetLink::from);
	    }

	    public Uni<Boolean> save(MySQLPool client, Long assetId, Long prosumerId, Long utilityOperatorId, String gridCellId, String status) {
	        return client.preparedQuery("INSERT INTO AssetLink(assetId, prosumerId, utilityOperatorId, gridCellId, status) VALUES (?,?,?,?,?)").execute(Tuple.of(assetId, prosumerId, utilityOperatorId, gridCellId, status))
	        		.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	    }

	    public static Uni<Boolean> delete(MySQLPool client, Long assetLinkId) {
	        return client.preparedQuery("DELETE FROM AssetLink WHERE assetLinkId = ?").execute(Tuple.of(assetLinkId))
	                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	    }

	    public static Uni<Boolean> update(MySQLPool client, Long assetLinkId, Long assetId, Long prosumerId, Long utilityOperatorId, String gridCellId, String status) {
	        return client.preparedQuery("UPDATE AssetLink SET assetId = ?, prosumerId = ?, utilityOperatorId = ?, gridCellId = ?, status = ? WHERE assetLinkId = ?").execute(Tuple.of(assetId, prosumerId, utilityOperatorId, gridCellId, status, assetLinkId))
	        		.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	    }

		public static Uni<Boolean> updateStatus(MySQLPool client, Long assetLinkId, String status) {
	        return client.preparedQuery("UPDATE AssetLink SET status = ? WHERE assetLinkId = ?").execute(Tuple.of(status, assetLinkId))
	        		.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1);
	    }  
}
