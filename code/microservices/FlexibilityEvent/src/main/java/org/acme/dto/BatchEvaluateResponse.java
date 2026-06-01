package org.acme.dto;

import org.acme.FlexibilityEvent;
import java.util.List;

public class BatchEvaluateResponse {
    public List<FlexibilityEvent> eventCreated;

    public BatchEvaluateResponse() {
    }

    public BatchEvaluateResponse(List<FlexibilityEvent> eventCreated) {
        this.eventCreated = eventCreated;
    }
}
