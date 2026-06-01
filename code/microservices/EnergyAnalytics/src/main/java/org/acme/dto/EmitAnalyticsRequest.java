package org.acme.dto;

import org.acme.entities.AverageSoC;
import org.acme.entities.ConsumedEnergyByProsumer;
import org.acme.entities.EnergyDischargedByZone;
import org.acme.entities.GeneratedEnergyByProsumer;

import java.util.List;

public class EmitAnalyticsRequest {
    public List<GeneratedEnergyByProsumer> generatedByProsumer;
    public List<ConsumedEnergyByProsumer> consumedByProsumer;
    public List<EnergyDischargedByZone> dischargedByZone;
    public AverageSoC averageSoC;

    public EmitAnalyticsRequest() {
    }

    public EmitAnalyticsRequest(List<GeneratedEnergyByProsumer> generatedByProsumer,
                                List<ConsumedEnergyByProsumer> consumedByProsumer,
                                List<EnergyDischargedByZone> dischargedByZone,
                                AverageSoC averageSoC) {
        this.generatedByProsumer = generatedByProsumer;
        this.consumedByProsumer = consumedByProsumer;
        this.dischargedByZone = dischargedByZone;
        this.averageSoC = averageSoC;
    }
}
