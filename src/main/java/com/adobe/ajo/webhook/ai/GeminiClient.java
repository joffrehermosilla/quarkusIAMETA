package com.adobe.ajo.webhook.ai;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
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
        public List<Content> contents;
        public GeminiRequest(String text) {
            this.contents = List.of(new Content(text));
        }
    }

    class Content {
        public List<Part> parts;
        public Content(String text) {
            this.parts = List.of(new Part(text));
        }
    }

    class Part {
        public String text;
        public Part(String text) {
            this.text = text;
        }
    }
}
