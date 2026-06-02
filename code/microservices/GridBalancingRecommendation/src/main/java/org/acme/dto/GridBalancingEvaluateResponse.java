package org.acme.dto;

import org.acme.entities.BalancingRecommendation;
import java.util.List;

public class GridBalancingEvaluateResponse {
    public List<BalancingRecommendation> eventCreated;
    public boolean hasGridBalancing;

    public GridBalancingEvaluateResponse(List<BalancingRecommendation> eventCreated) {
        this.eventCreated = eventCreated;
        this.hasGridBalancing = !eventCreated.isEmpty();
    }
}
