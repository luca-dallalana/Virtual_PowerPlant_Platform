package org.acme.dto;

public class AssetLinkRegistrationResponse {
    public Long assetLinkId;
    public Long assetId;
    public Long prosumerId;
    public Long utilityOperatorId;
    public String gridCellId;
    public String status;
    public String publishTo;

    public AssetLinkRegistrationResponse() {}

    public AssetLinkRegistrationResponse(Long assetLinkId, Long assetId, Long prosumerId,
                                        Long utilityOperatorId, String gridCellId,
                                        String status, String publishTo) {
        this.assetLinkId = assetLinkId;
        this.assetId = assetId;
        this.prosumerId = prosumerId;
        this.utilityOperatorId = utilityOperatorId;
        this.gridCellId = gridCellId;
        this.status = status;
        this.publishTo = publishTo;
    }
}
