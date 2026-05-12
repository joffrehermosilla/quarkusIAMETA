package com.adobe.ajo.webhook.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public interface MetaEventModels {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MetaEvent(List<Entry> entry) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(List<Change> changes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Change(Value value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Value(List<Message> messages, List<Contact> contacts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(
        String id, 
        String type, 
        Context context, 
        Button button, 
        Text text
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contact(
        @JsonProperty("wa_id") String waId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Context(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Button(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Text(String body) {}
}
