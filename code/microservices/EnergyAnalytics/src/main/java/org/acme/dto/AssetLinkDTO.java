package org.acme.dto;

public class AssetLinkDTO {
    public Long assetLinkId;
    public Long assetId;
    public Long prosumerId;
    public Long utilityOperatorId;
    public String gridCellId;
    public String status;

    public AssetLinkDTO() {
    }

    public AssetLinkDTO(Long assetLinkId, Long assetId, Long prosumerId, Long utilityOperatorId,
                       String gridCellId, String status) {
        this.assetLinkId = assetLinkId;
        this.assetId = assetId;
        this.prosumerId = prosumerId;
        this.utilityOperatorId = utilityOperatorId;
        this.gridCellId = gridCellId;
        this.status = status;
    }
}
