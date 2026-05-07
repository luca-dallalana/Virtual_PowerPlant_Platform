package org.acme.dto;

import java.time.LocalDateTime;

public class ForecastRequest {
    public String analysisType;
    public String eventType;
    public Long assetId;
    public String recommendedAction;
    public LocalDateTime startDate;
    public LocalDateTime endDate;
    public String customQuestion;
}
