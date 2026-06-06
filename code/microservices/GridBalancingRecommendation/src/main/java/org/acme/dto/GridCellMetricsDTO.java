package org.acme.dto;

public class GridCellMetricsDTO {
    public String gridCellId;
    public Double maxCapacity;
    public Double netLoad;
    public Double headroom;

    public GridCellMetricsDTO() {}

    public GridCellMetricsDTO(String gridCellId, Double maxCapacity, Double netLoad, Double headroom) {
        this.gridCellId = gridCellId;
        this.maxCapacity = maxCapacity;
        this.netLoad = netLoad;
        this.headroom = headroom;
    }
}
