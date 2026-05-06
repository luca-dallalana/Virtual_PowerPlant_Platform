package org.acme.dto;

import java.time.LocalDateTime;

public class AnalyticsResult {
    public String status;
    public LocalDateTime timestamp;
    public Integer recordsProcessed;

    public AnalyticsResult() {
    }

    public AnalyticsResult(String status, LocalDateTime timestamp, Integer recordsProcessed) {
        this.status = status;
        this.timestamp = timestamp;
        this.recordsProcessed = recordsProcessed;
    }
}
