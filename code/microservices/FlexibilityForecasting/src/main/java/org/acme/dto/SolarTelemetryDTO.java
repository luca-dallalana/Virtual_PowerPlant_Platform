package org.acme.dto;

import java.time.LocalDateTime;

public class SolarTelemetryDTO {
    public Long id;
    public Long asset_id;
    public String asset_type;
    public String grid_cell_id;
    public Float Current_Generation;
    public Float Daily_Total;
    public LocalDateTime timeStamp;
}
