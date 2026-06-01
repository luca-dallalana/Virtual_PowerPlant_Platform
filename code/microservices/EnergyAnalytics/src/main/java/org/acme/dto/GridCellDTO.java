package org.acme.dto;

public class GridCellDTO {
    public String gridCellId;
    public Long utilityOperatorId;
    public Double maxCapacity;
    public String geographicBoundaries;

    public GridCellDTO() {
    }

    public GridCellDTO(String gridCellId, Long utilityOperatorId, Double maxCapacity, String geographicBoundaries) {
        this.gridCellId = gridCellId;
        this.utilityOperatorId = utilityOperatorId;
        this.maxCapacity = maxCapacity;
        this.geographicBoundaries = geographicBoundaries;
    }
}
