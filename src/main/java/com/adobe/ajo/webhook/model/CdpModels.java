package com.adobe.ajo.webhook.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public interface CdpModels {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CdpPayload(

            @JsonProperty("_id") String id,

            String timestamp,

            @JsonProperty("_bcp") Bcp bcp

    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Bcp(

            IdentityMap identityMap,

            Transient transientData

    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IdentityMap(

            List<WhatsappIdentity> whatsapp

    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record WhatsappIdentity(

            String id,

            boolean primary

    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Transient(

            Customer customer

    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Customer(

            Feedback feedback

    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Feedback(

            String channel,

            String messageType,

            String buttonReply,

            String message,

            String templateWamId,

            String wamId,

            String conversationId,

            String campaign,

            String source

    ) {
    }

}