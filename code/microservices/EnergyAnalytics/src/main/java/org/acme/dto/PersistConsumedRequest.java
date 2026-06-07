package org.acme.dto;

import org.acme.entities.ConsumedEnergyByProsumer;
import java.util.List;

public class PersistConsumedRequest {
    public List<ConsumedEnergyByProsumer> consumedByProsumer;

    public PersistConsumedRequest() {
    }
}
