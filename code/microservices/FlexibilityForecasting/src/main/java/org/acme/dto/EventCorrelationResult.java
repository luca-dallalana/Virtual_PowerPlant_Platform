package org.acme.dto;

import java.util.List;
import java.util.Map;

public class EventCorrelationResult {
    public List<FlexibilityEventDTO> flexibilityLogs;
    public List<BalancingRecommendationDTO> gridBalancingLogs;
    public List<SolarAssetDTO> solarAssets;
    public List<SolarTelemetryDTO> solarTelemetry;

    public int totalFlexibilityEvents;
    public int totalGridBalancingLogs;
    public int correlatedOutcomes;
    public double successRate;

    public int solarAssetCount;
    public double avgCurrentGenerationKw;
    public double totalDailyGenerationKwh;
    public Map<String, Double> solarGenerationByGridCell;

    public List<CorrelatedEventEntry> correlatedEvents;
}
