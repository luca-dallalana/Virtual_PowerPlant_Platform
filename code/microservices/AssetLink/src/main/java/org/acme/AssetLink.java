package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import java.util.List;
import java.util.stream.Collectors;

public class AssetLink {
	
	    public Long id;
		public Long idProsumer;
		public Long idUtilityOperator;

	    public AssetLink() {
	    }


		public AssetLink(Long id, Long idProsumer_received, Long idUtilityOperator_received) {
			this.id = id;
			this.idProsumer = idProsumer_received;
			this.idUtilityOperator = idUtilityOperator_received;
		}


		@Override
		public String toString() {
			return "{id:" + id + ", idProsumer:" + idProsumer + ", idUtilityOperator:" + idUtilityOperator + "}\n";
		}

		private static AssetLink from(Row row) {
	        return new AssetLink(row.getLong("id"), row.getLong("idProsumer") , row.getLong("idUtilityOperator"));
	    }
	    
	    public static Multi<AssetLink> findAll(MySQLPool client) {
	        return client.query("SELECT id, idProsumer, idUtilityOperator  FROM AssetLink ORDER BY id ASC").execute()
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(AssetLink::from);
	    }

	    public static Multi<AssetLink> findByUtilityOperatorId(MySQLPool client, Long utilityOperatorId) {
	        return client.preparedQuery("SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idUtilityOperator = ?")
	                .execute(Tuple.of(utilityOperatorId))
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(AssetLink::from);
	    }
	    
	    public static Uni<AssetLink> findById(MySQLPool client, Long id) {
	        return client.preparedQuery("SELECT id, idProsumer, idUtilityOperator  FROM AssetLink WHERE id = ?").execute(Tuple.of(id)) 
	                .onItem().transform(RowSet::iterator) 
	                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null); 
	    }

		public static Uni<AssetLink> findById2(MySQLPool client, Long idProsumer_R , Long idUtilityOperator_R) {
	        return client.preparedQuery("SELECT id, idProsumer, idUtilityOperator FROM AssetLink WHERE idProsumer = ? AND idUtilityOperator = ?").execute(Tuple.of(idProsumer_R , idUtilityOperator_R)) 
	                .onItem().transform(RowSet::iterator) 
	                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null); 
					
	    }

	public static Multi<Long> findProsumerIdsByOperatorId(MySQLPool client, Long utilityOperatorId) {
		return client.preparedQuery("SELECT idProsumer FROM AssetLink WHERE idUtilityOperator = ?")
				.execute(Tuple.of(utilityOperatorId))
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(row -> row.getLong("idProsumer"));
	}

	public static Multi<Long> findProsumerIdsByOperatorIds(MySQLPool client, List<Long> utilityOperatorIds) {
		if (utilityOperatorIds == null || utilityOperatorIds.isEmpty()) {
			return Multi.createFrom().empty();
		}
		String placeholders = utilityOperatorIds.stream().map(id -> "?").collect(Collectors.joining(", "));
		Tuple params = Tuple.tuple();
		utilityOperatorIds.forEach(params::addLong);
		return client.preparedQuery("SELECT DISTINCT idProsumer FROM AssetLink WHERE idUtilityOperator IN (" + placeholders + ")")
				.execute(params)
				.onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
				.onItem().transform(row -> row.getLong("idProsumer"));
	}

public Uni<Long> save(MySQLPool client , Long idProsumer_R , Long idUtilityOperator_R)
	{
        return client.preparedQuery("INSERT INTO AssetLink(idProsumer,idUtilityOperator) VALUES (?,?)").execute(Tuple.of( idProsumer_R , idUtilityOperator_R))
        		.onItem().transform(result -> (Long) result.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)); 
    }
    
    public static Uni<Boolean> delete(MySQLPool client, Long id_R) {
        return client.preparedQuery("DELETE FROM AssetLink WHERE id = ?").execute(Tuple.of(id_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1); 
    }
	    
	    public static Uni<Boolean> update(MySQLPool client, Long id_R, Long idProsumer_R , Long idUtilityOperator_R ) {
	        return client.preparedQuery("UPDATE AssetLink SET idProsumer = ? , idUtilityOperator = ? WHERE id = ?").execute(Tuple.of( idProsumer_R , idUtilityOperator_R,id_R))
	        		.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1 ); 
	    }  
}