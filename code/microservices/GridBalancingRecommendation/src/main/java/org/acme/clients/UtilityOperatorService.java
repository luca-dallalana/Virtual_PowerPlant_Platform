package org.acme.clients;

import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.dto.GridCellDTO;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/UtilityOperator")
@RegisterRestClient(configKey="utility-operator-api")
public interface UtilityOperatorService {
    @GET
    @Path("gridcell")
    Multi<GridCellDTO> getAllGridCells();
}
