package org.acme.dto;

import java.util.List;

public class ComputeGeneratedRequest {
    public List<AssetDTO> assets;
    public List<TelemetryDTO> telemetry;

    public ComputeGeneratedRequest() {
    }

    public ComputeGeneratedRequest(List<AssetDTO> assets, List<TelemetryDTO> telemetry) {
        this.assets = assets;
        this.telemetry = telemetry;
    }
}
