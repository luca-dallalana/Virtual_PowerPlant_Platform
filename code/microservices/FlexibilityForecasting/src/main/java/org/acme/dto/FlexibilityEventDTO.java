package org.acme.dto;

import java.time.LocalDateTime;

public class FlexibilityEventDTO {
    public Long id;
    public Long assetId;
    public Long prosumerId;
    public String eventType;
    public Float soc_percent;
    public Float soh_percent;
    public String recommendedAction;
    public String marketPriceLevel;
    public String gridCellId;
    public LocalDateTime timestamp;
}
