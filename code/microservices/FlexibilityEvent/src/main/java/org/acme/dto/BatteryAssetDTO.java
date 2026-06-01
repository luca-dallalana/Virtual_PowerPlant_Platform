package org.acme.dto;

public class BatteryAssetDTO {
    public Long prosumerId;
    public Long assetId;

    public BatteryAssetDTO() {
    }

    public BatteryAssetDTO(Long prosumerId, Long assetId) {
        this.prosumerId = prosumerId;
        this.assetId = assetId;
    }
}
