package org.acme.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ForecastRequest {
    public String analysisType;
    public String eventType;
    public Long assetId;
    public String recommendedAction;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public String customQuestion;
    public List<FlexibilityEventDTO> events;
    public List<BalancingRecommendationDTO> recommendations;
}
