package org.acme.dto;

import java.time.LocalDateTime;

public class FlexibilityEventDTO {
    public Long id;
    public Long assetId;
    public Long prosumerId;
    public String eventType;
    public Float soc_percent;
    public String recommendedAction;
    public Float marketPrice;
    public Float incentiveAmount;
    public String gridCellId;
    public LocalDateTime timestamp;
}
