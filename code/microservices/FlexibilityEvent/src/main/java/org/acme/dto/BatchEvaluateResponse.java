package org.acme.dto;

import org.acme.FlexibilityEvent;
import java.util.List;

public class BatchEvaluateResponse {
    public List<FlexibilityEvent> eventCreated;
    public boolean suggestedOffers;

    public BatchEvaluateResponse() {
    }

    public BatchEvaluateResponse(List<FlexibilityEvent> eventCreated) {
        this.eventCreated = eventCreated;
        this.suggestedOffers = eventCreated != null && !eventCreated.isEmpty();
    }
}
