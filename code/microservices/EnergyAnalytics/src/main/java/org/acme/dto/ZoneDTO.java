package org.acme.dto;

public class ZoneDTO {
    public String gridCellId;
    public String operatorName;
    public String operatorLocation;

    public ZoneDTO() {
    }

    public ZoneDTO(String gridCellId, String operatorName, String operatorLocation) {
        this.gridCellId = gridCellId;
        this.operatorName = operatorName;
        this.operatorLocation = operatorLocation;
    }
}
