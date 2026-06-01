package org.acme.dto;

import java.util.List;

public class ComputeDischargedRequest {
    public List<GridCellDTO> zones;
    public List<TelemetryDTO> telemetry;

    public ComputeDischargedRequest() {
    }

    public ComputeDischargedRequest(List<GridCellDTO> zones, List<TelemetryDTO> telemetry) {
        this.zones = zones;
        this.telemetry = telemetry;
    }
}
