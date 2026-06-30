package com.adobe.ajo.webhook.cdp;

import com.adobe.ajo.webhook.model.CdpModels.CdpPayload;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "cdp-client")
public interface CdpClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)

    @ClientHeaderParam(name = "Authorization", value = "Bearer ${cdp.auth.token}")

    @ClientHeaderParam(name = "x-adobe-flow-id", value = "${cdp.flow.id}")

    @ClientHeaderParam(name = "Accept", value = "application/json")

    @ClientHeaderParam(name = "Content-Type", value = "application/json")

    Uni<Response> sendEvent(CdpPayload payload);

}