package org.acme.dto;

import java.util.List;

public class ComputeAverageSoCRequest {
    public List<TelemetryDTO> telemetry;

    public ComputeAverageSoCRequest() {
    }

    public ComputeAverageSoCRequest(List<TelemetryDTO> telemetry) {
        this.telemetry = telemetry;
    }
}
