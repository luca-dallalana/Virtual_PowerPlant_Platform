package org.acme.dto;

import java.util.List;

public class GridCellMetricsRequest {
    public GridCellDTO gridCell;
    public List<TelemetryDTO> telemetryData;
    public List<GridCellDTO> neighbourCells;
    public List<TelemetryDTO> allTelemetry;

    public GridCellMetricsRequest() {}
}
