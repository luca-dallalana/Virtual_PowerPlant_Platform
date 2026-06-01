package org.acme.dto;

import java.util.List;

public class GridBalancingEvaluateRequest {
    public GridCellDTO sourceCell;
    public List<GridCellDTO> neighbourCells;
    public List<TelemetryDTO> allTelemetry;

    public GridBalancingEvaluateRequest() {}
}
