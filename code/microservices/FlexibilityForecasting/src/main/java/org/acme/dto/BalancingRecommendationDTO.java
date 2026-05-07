package org.acme.dto;

import java.time.LocalDateTime;

public class BalancingRecommendationDTO {
    public Long id;
    public String sourceGridCellId;
    public String targetGridCellId;
    public Double sourceNetLoadKw;
    public Double targetHeadroomKw;
    public Double overloadKw;
    public String status;
    public String rationale;
    public LocalDateTime createdAt;
}
