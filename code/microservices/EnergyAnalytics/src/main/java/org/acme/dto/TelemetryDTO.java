package org.acme.dto;

import java.time.LocalDateTime;

public class TelemetryDTO {
    public Long id;
    public LocalDateTime timeStamp;
    public Long asset_id;
    public String asset_type;
    public String grid_cell_id;
    public Float State_of_Charge;
    public Float Available_Energy;
    public Float Current_Output;
    public Float Max_Capacity;
    public Float State_of_Health;
    public String Status;
    public Float Current_Generation;
    public Float Daily_Total;
    public Float Grid_Voltage;
    public Float Frequency;
    public String Plug_Status;
    public Float Charging_Rate;
    public Float Session_Energy;
    public Float EV_SoC;

    public TelemetryDTO() {
    }
}
