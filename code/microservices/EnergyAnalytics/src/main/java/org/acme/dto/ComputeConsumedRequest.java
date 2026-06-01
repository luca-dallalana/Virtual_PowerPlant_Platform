package org.acme.dto;

import java.util.List;

public class ComputeConsumedRequest {
    public List<AssetDTO> assets;
    public List<TelemetryDTO> telemetry;

    public ComputeConsumedRequest() {
    }

    public ComputeConsumedRequest(List<AssetDTO> assets, List<TelemetryDTO> telemetry) {
        this.assets = assets;
        this.telemetry = telemetry;
    }
}
