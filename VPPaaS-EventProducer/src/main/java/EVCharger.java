import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.json.JSONObject;

public class EVCharger extends Message 
{
    public enum Level {AVAILABLE, OCCUPIED, CHARGING, FAULTED};
    
    private Double ChargingRate;
    private Double SessionEnergy;
    private Double EVSoC;
    private Level PlugStatus;
  
    public Double getChargingRate() {        return ChargingRate;    }
    public Double getSessionEnergy() {        return SessionEnergy;    }
    public Double getEVSoC() {        return EVSoC;    }
    public Level getPlugStatus() {        return PlugStatus;    }

    public Level randomLevel() 
    {
        Level[] allpossibilities =  Level.values();   
        return allpossibilities[ThreadLocalRandom.current().nextInt(Level.values().length)];
    }

    @Override
    public String toStringAsJSON()
    {        
        JSONObject msg = super.toJSON();

        JSONObject inner_msg = new JSONObject();
        inner_msg.put("connector_status",getPlugStatus());
        inner_msg.put("charging_power_kw",getChargingRate());
        inner_msg.put("session_energy_kwh",getSessionEnergy());
        inner_msg.put("ev_soc_percent",getEVSoC());

        msg.put("payload",inner_msg);

        return msg.toString();
    }

    public EVCharger(LocalDateTime timeStamp, String asset_id, String grid_cell_id,
                         Double ChargingRate, Double SessionEnergy, Double EVSoC, Level PlugStatus) {
        super(timeStamp, asset_id, "EV_CHARGER", grid_cell_id);
        this.ChargingRate = ChargingRate;
        this.SessionEnergy = SessionEnergy;
        this.EVSoC = EVSoC;
        this.PlugStatus = PlugStatus;
    }

    public EVCharger() 
    {
        super();
    }  
}
