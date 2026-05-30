package org.acme.dto;

public class AssetDTO {
    public Long assetId;
    public Long prosumerId;

    public AssetDTO() {
    }

    public AssetDTO(Long assetId, Long prosumerId) {
        this.assetId = assetId;
        this.prosumerId = prosumerId;
    }
}
