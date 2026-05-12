package com.adobe.ajo.webhook.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public interface CdpModels {

    record CdpPayload(
        @JsonProperty("_bcp") Bcp bcp,
        @JsonProperty("_id") String id,
        String timestamp
    ) {}

    record Bcp(
        IdentityMap identityMap,
        Transient transientData
    ) {}

    record IdentityMap(
        List<WhatsappIdentity> whatsapp
    ) {}

    record WhatsappIdentity(
        String id,
        boolean primary
    ) {}

    record Transient(
        Customer customer
    ) {}

    record Customer(
        Feedback feedback
    ) {}

    record Feedback(
        String buttonReply,
        String channel,
        String templateWamId,
        String wamId
    ) {}
}
