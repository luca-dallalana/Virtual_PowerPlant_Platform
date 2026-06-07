package org.acme.dto;

public class BuildPromptRequest {
    public Long eventId;
    public Long assetId;
    public String eventType;
    public Float socAtEventTime;
    public Float sohAtEventTime;
    public String recommendedAction;
    public String marketPriceLevel;
    public String gridCellId;

    public Float currentSoc;
    public Float currentOutputKw;
    public String currentStatus;
}
