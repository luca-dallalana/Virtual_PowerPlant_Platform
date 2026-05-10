package org.acme.clients;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.dto.OllamaRequest;
import org.acme.dto.OllamaResponse;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api")
@RegisterRestClient(configKey="ollama-api")
public interface OllamaService {
    @POST
    @Path("/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<OllamaResponse> generate(OllamaRequest request);
}
