package org.acme.dto;

import java.util.List;

public class AnalyticsRequest {
    public List<TelemetryDTO> telemetryData;
    public List<AssetLinkDTO> assetLinks;

    public AnalyticsRequest() {
    }

    public AnalyticsRequest(List<TelemetryDTO> telemetryData, List<AssetLinkDTO> assetLinks) {
        this.telemetryData = telemetryData;
        this.assetLinks = assetLinks;
    }
}
