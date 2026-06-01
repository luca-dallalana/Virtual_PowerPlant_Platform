package org.acme.dto;

public class GridCellEvaluationResult {
    public boolean needGridBalancing;

    public GridCellEvaluationResult() {
    }

    public GridCellEvaluationResult(boolean needGridBalancing) {
        this.needGridBalancing = needGridBalancing;
    }
}
