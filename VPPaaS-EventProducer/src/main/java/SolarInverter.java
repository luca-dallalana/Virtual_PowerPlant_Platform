import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import org.json.JSONObject;

public class SolarInverter extends Message 
{
   
    private Double CurrentGeneration;
    private Double DailyTotal;
    private Double GridVoltage;
    private Double Frequency;
  
    public Double getCurrentGeneration() {        return CurrentGeneration;    }
    public Double getDailyTotal() {         return DailyTotal;    }
    public Double getGridVoltage() {        return GridVoltage;    }
    public Double getFrequency() {        return Frequency;    }

    @Override
    public String toStringAsJSON()
    {        
        JSONObject msg = super.toJSON();

        JSONObject inner_msg = new JSONObject();
        inner_msg.put("generation_kw",getCurrentGeneration());
        inner_msg.put("daily_yield_kwh",getDailyTotal());
        inner_msg.put("ac_voltage_v",getGridVoltage());
        inner_msg.put("grid_frequency_hz",getFrequency());

        msg.put("payload",inner_msg);

        return msg.toString();
    }

    public SolarInverter(LocalDateTime timeStamp, String asset_id, String grid_cell_id,
                         Double CurrentGeneration, Double DailyTotal, Double GridVoltage, Double Frequency) {
        super(timeStamp, asset_id, "SOLAR", grid_cell_id);
        this.CurrentGeneration = CurrentGeneration;
        this.DailyTotal = DailyTotal;
        this.GridVoltage = GridVoltage;
        this.Frequency = Frequency;
    }

    public SolarInverter() 
    {
        super();
    }  
}
