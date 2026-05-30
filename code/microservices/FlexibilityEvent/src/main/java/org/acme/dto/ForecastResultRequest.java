package org.acme.dto;

public class ForecastResultRequest {
    public String windowStart;
    public String windowEnd;
    public Integer flexibilityEventsCount;
    public Integer gridBalancingCount;
    public String forecastResult;

    public ForecastResultRequest() {}
}
