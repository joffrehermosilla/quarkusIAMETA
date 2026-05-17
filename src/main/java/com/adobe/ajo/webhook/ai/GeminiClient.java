package com.adobe.ajo.webhook.ai;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@RegisterRestClient(configKey = "gemini-api")
@Path("/v1beta/models/gemini-1.5-flash:generateContent")
public interface GeminiClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> generate(@QueryParam("key") String apiKey, GeminiRequest request);

    class GeminiRequest {
        @JsonProperty("contents")
        private List<Content> contents;

        public GeminiRequest() {}
        public GeminiRequest(String text) {
            this.contents = List.of(new Content(text));
        }

        public List<Content> getContents() { return contents; }
        public void setContents(List<Content> contents) { this.contents = contents; }
    }

    class Content {
        @JsonProperty("parts")
        private List<Part> parts;

        public Content() {}
        public Content(String text) {
            this.parts = List.of(new Part(text));
        }

        public List<Part> getParts() { return parts; }
        public void setParts(List<Part> parts) { this.parts = parts; }
    }

    class Part {
        @JsonProperty("text")
        private String text;

        public Part() {}
        public Part(String text) {
            this.text = text;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
