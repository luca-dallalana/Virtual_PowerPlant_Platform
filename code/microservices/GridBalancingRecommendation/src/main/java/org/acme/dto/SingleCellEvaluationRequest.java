package org.acme.dto;

import java.util.List;

public class SingleCellEvaluationRequest {
    public GridCellDTO gridCell;
    public List<TelemetryDTO> telemetryData;

    public SingleCellEvaluationRequest() {
    }

    public SingleCellEvaluationRequest(GridCellDTO gridCell, List<TelemetryDTO> telemetryData) {
        this.gridCell = gridCell;
        this.telemetryData = telemetryData;
    }
}
