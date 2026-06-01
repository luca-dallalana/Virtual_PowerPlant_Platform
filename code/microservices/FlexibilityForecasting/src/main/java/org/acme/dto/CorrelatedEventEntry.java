package org.acme.dto;

import java.util.List;

public class CorrelatedEventEntry {
    public FlexibilityEventDTO event;
    public List<BalancingRecommendationDTO> recommendations;
    public Double solarGenerationKw;
}
