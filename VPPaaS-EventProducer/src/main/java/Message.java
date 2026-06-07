import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.json.JSONObject;

public class Message {
	
	private UUID seqkey;
	private LocalDateTime timeStamp;	//ISO8601
	private String asset_id;
	private String asset_type;
	private String grid_cell_id;
	
	public String getAsset_id() {		return asset_id;	}
	public String getAsset_type() {		return asset_type;	}
	public String getGrid_cell_id() {		return grid_cell_id;	}
	public LocalDateTime getTimeStamp() {		return this.timeStamp;	}
	public UUID getSeqkey() {		return seqkey;	}

	public String getTimeStampAsText() 
	{
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
		String formattedDate = timeStamp.format(formatter);
		return (formattedDate);
	}

	public JSONObject toJSON()
    {        
        JSONObject msg = new JSONObject();
		
		msg.put("timeStamp",getTimeStampAsText());
		msg.put("asset_id",getAsset_id());
		msg.put("asset_type",getAsset_type());
		msg.put("grid_cell_id",getGrid_cell_id());
	
        return msg;
    }
	
	public String toStringAsJSON()    {			return toJSON().toString(); 	}
	
	public Message(LocalDateTime timeStamp, String asset_id, String asset_type, String grid_cell_id) {
		this.seqkey = UUID.randomUUID();
		this.timeStamp = timeStamp;
		this.asset_id = asset_id;
		this.asset_type = asset_type;
		this.grid_cell_id = grid_cell_id;		
	}

	public Message() {};	
}
