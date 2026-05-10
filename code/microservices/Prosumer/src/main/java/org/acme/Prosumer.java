package org.acme;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;

public class Prosumer {
	
	 	public Long FiscalNumber;
	    public Long id;
		public String location;
		public String name;

	    public Prosumer() {
	    }

	    public Prosumer(String name) {
	        this.name = name;
	    }

	    public Prosumer(Long id, String name) {
	        this.id = id;
	        this.name = name;
	    }
		
	    public Prosumer(Long iD, String name_R , String location_R , Long FiscalNumber_R ) {
			FiscalNumber = FiscalNumber_R;
			id = iD;
			location = location_R;
			name = name_R;
		}

		@Override
		public String toString() {
			return "{FiscalNumber:" + FiscalNumber + ", id:" + id + ", location:" + location + ", name:" + name
					+ "}\n";
		}

		private static Prosumer from(Row row) {
	        return new Prosumer(row.getLong("id"), row.getString("name") , row.getString("location") , row.getLong("FiscalNumber") );
	    }
	    
	    public static Multi<Prosumer> findAll(MySQLPool client) {
	        return client.query("SELECT id, name, FiscalNumber , location FROM Prosumer ORDER BY id ASC").execute()
	                .onItem().transformToMulti(set -> Multi.createFrom().iterable(set))
	                .onItem().transform(Prosumer::from);
	    }
	    
	    public static Uni<Prosumer> findById(MySQLPool client, Long id) {
	        return client.preparedQuery("SELECT id, name, FiscalNumber , location FROM Prosumer WHERE id = ?").execute(Tuple.of(id)) 
	                .onItem().transform(RowSet::iterator) 
	                .onItem().transform(iterator -> iterator.hasNext() ? from(iterator.next()) : null); 
	    }
	    
public Uni<Long> save(MySQLPool client , String name_R, Long fnumber , String loc) 
	{
        return client.preparedQuery("INSERT INTO Prosumer(name,FiscalNumber,location) VALUES (?,?,?)").execute(Tuple.of(name_R ,fnumber , loc))
        		.onItem().transform(result -> (Long) result.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID)); 
    }
    
    public static Uni<Boolean> delete(MySQLPool client, Long id_R) {
        return client.preparedQuery("DELETE FROM Prosumer WHERE id = ?").execute(Tuple.of(id_R))
                .onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1); 
    }
	    
	    public static Uni<Boolean> update(MySQLPool client, Long id_R, String name_R, Long fnumber , String loc) {
	        return client.preparedQuery("UPDATE Prosumer SET name = ?, FiscalNumber = ? , location = ? WHERE id = ?").execute(Tuple.of(name_R,fnumber,loc,id_R))
	        		.onItem().transform(pgRowSet -> pgRowSet.rowCount() == 1 ); 
	    }  
}
