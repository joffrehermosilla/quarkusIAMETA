package com.adobe.ajo.webhook.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public interface CdpModels {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        record CdpPayload(

                        @JsonProperty("_id") String id,

                        String eventType,

                        String timestamp,

                        @JsonProperty("_bcp") Bcp bcp

        ) {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        record Bcp(

                        Identity identity,

                        Transient transientData

        ) {
        }

        record Identity(

                        String customerId

        ) {
        }

        record Transient(

                        Customer customer

        ) {
        }

        record Customer(

                        Feedback feedback

        ) {
        }

        record Feedback(

                        String reply,

                        String channel,

                        String templateName,

                        String templateWamId,

                        String wamId

        ) {
        }
}