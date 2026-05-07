package org.acme.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class ForecastResult {
    public Long id;
    public String analysisType;
    public String question;
    public String aiResponse;
    public String sentiment;
    public Double confidenceScore;
    public Integer eventsAnalyzed;
    public Integer correlatedOutcomes;
    public Double successRate;
    public LocalDateTime analysisTimestamp;
    public Map<String, Object> insights;
}
