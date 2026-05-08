package org.acme.dto;

import java.util.List;

public class GridBalancingRequest {
    public List<GridCellDTO> gridCells;
    public List<TelemetryDTO> telemetryData;

    public GridBalancingRequest() {
    }

    public GridBalancingRequest(List<GridCellDTO> gridCells, List<TelemetryDTO> telemetryData) {
        this.gridCells = gridCells;
        this.telemetryData = telemetryData;
    }
}
