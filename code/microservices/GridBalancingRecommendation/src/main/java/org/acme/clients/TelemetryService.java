package org.acme.clients;

import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.dto.TelemetryDTO;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/Telemetry")
@RegisterRestClient(configKey="telemetry-api")
public interface TelemetryService {
    @GET
    Multi<TelemetryDTO> getAllTelemetry();
}
