package org.acme;

public class AssetDTO {
    public Long assetId;
    public Long prosumerId;
    public String assetType;

    public AssetDTO(Long assetId, Long prosumerId, String assetType) {
        this.assetId = assetId;
        this.prosumerId = prosumerId;
        this.assetType = assetType;
    }
}
