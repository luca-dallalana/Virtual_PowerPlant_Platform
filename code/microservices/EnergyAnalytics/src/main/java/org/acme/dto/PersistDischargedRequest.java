package org.acme.dto;

import org.acme.entities.EnergyDischargedByZone;
import java.util.List;

public class PersistDischargedRequest {
    public List<EnergyDischargedByZone> dischargedByZone;

    public PersistDischargedRequest() {
    }
}
