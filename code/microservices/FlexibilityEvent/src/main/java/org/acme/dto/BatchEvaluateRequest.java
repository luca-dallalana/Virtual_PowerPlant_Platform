package org.acme.dto;

import org.acme.TelemetryDTO;
import java.util.List;

public class BatchEvaluateRequest {
    public List<BatteryAssetDTO> batteryAssets;
    public List<TelemetryDTO> telemetryList;
}
