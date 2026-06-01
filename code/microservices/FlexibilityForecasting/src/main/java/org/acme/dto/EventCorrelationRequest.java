package org.acme.dto;

import java.util.List;

public class EventCorrelationRequest {
    public List<FlexibilityEventDTO> flexibilityLogs;
    public List<BalancingRecommendationDTO> gridBalancingLogs;
    public List<SolarAssetDTO> solarAssets;
    public List<SolarTelemetryDTO> solarTelemetry;
}
