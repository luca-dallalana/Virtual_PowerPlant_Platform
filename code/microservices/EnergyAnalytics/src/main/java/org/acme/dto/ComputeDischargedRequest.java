package org.acme.dto;

import java.util.List;

public class ComputeDischargedRequest {
    public List<ZoneDTO> zones;
    public List<TelemetryDTO> telemetry;

    public ComputeDischargedRequest() {
    }

    public ComputeDischargedRequest(List<ZoneDTO> zones, List<TelemetryDTO> telemetry) {
        this.zones = zones;
        this.telemetry = telemetry;
    }
}
