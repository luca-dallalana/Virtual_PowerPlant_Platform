package org.acme;

public class ActiveAssetDTO {
    public Long assetId;
    public Long prosumerId;
    public String assetType;

    public ActiveAssetDTO(Long assetId, Long prosumerId, String assetType) {
        this.assetId = assetId;
        this.prosumerId = prosumerId;
        this.assetType = assetType;
    }
}
