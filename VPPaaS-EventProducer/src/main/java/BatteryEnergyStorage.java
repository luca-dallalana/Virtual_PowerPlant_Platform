import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.json.JSONObject;


public class BatteryEnergyStorage extends Message
{
    public enum Level {ONLINE, OFFLINE, FAULT, MAINTENANCE};
  
    private Double StateofCharge;
    private Double AvailableEnergy;
    private Double CurrentOutput;
    private Double MaxCapacity;
    private Double StateofHealth;
    private Level Status;

    public Level randomLevel() 
    {
        Level[] allpossibilities =  Level.values();   
        return allpossibilities[ThreadLocalRandom.current().nextInt(Level.values().length)];
    }

    public Double getStateofCharge() {        return StateofCharge;    }
    public Double getAvailableEnergy() {        return AvailableEnergy;    }
    public Double getCurrentOutput() {        return CurrentOutput;    }
    public Double getMaxCapacity() {        return MaxCapacity;    }
    public Double getStateofHealth() {        return StateofHealth;    }
    public Level getStatus() {        return Status;    }
  
    @Override
    public String toStringAsJSON()
    {        
        JSONObject msg = super.toJSON();

        JSONObject inner_msg = new JSONObject();
        inner_msg.put("soc_percent",getStateofCharge());
        inner_msg.put("energy_available_kwh",getAvailableEnergy());
        inner_msg.put("active_power_kw",getCurrentOutput());
        inner_msg.put("max_discharge_power_kw",getMaxCapacity());
        inner_msg.put("soh_percent",getStateofHealth());
        inner_msg.put("connection_status",getStatus());

        msg.put("payload",inner_msg);

        return msg.toString();
    }

    public BatteryEnergyStorage(LocalDateTime timeStamp, String asset_id, String grid_cell_id,
                                Double stateofCharge, Double availableEnergy, Double currentOutput, Double maxCapacity,
                                Double stateofHealth, Level status) {
        super(timeStamp, asset_id, "BATTERY", grid_cell_id);
        StateofCharge = stateofCharge;
        AvailableEnergy = availableEnergy;
        CurrentOutput = currentOutput;
        MaxCapacity = maxCapacity;
        StateofHealth = stateofHealth;
        Status = status;
    }

    public BatteryEnergyStorage() 
    {
        super();
    }  
}
