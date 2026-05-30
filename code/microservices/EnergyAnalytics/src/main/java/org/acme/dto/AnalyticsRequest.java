package org.acme.dto;

import java.util.List;

public class AnalyticsRequest {
    public List<TelemetryDTO> telemetryData;
    public List<AssetDTO> assets;
    public List<ZoneDTO> zones;

    public AnalyticsRequest() {
    }

    public AnalyticsRequest(List<TelemetryDTO> telemetryData, List<AssetDTO> assets, List<ZoneDTO> zones) {
        this.telemetryData = telemetryData;
        this.assets = assets;
        this.zones = zones;
    }
}
