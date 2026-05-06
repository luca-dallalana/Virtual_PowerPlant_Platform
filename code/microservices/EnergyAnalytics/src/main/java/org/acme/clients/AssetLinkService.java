package org.acme.clients;

import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.dto.AssetLinkDTO;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/AssetLink")
@RegisterRestClient(configKey="assetlink-api")
public interface AssetLinkService {
    @GET
    Multi<AssetLinkDTO> get();
}
