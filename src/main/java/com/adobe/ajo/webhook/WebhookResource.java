package com.adobe.ajo.webhook;

import com.adobe.ajo.webhook.api.WebhookApi;
import com.adobe.ajo.webhook.ai.AiFallbackService;
import com.adobe.ajo.webhook.cdp.CdpClient;
import com.adobe.ajo.webhook.db.MetaEventLog;
import com.adobe.ajo.webhook.model.CdpModels;
import com.adobe.ajo.webhook.model.generated.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class WebhookResource implements WebhookApi {

    @RestClient
    CdpClient cdpClient;

    @Inject
    AiFallbackService aiService;

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "meta.verify.token")
    String verifyToken;

    @Override
    public Response verifyHandshake(String hubMode, String hubVerifyToken, String hubChallenge) {
        System.out.println(">>> Handshake request received");
        if ("subscribe".equals(hubMode) && verifyToken.equals(hubVerifyToken)) {
            return Response.ok(hubChallenge).build();
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    @Override
    public Response handleEvent(MetaEvent event) {
        System.out.println(">>> WEBHOOK POST RECEIVED");
        return Optional.ofNullable(event.getEntry())
                .filter(entries -> !entries.isEmpty())
                .map(entries -> entries.get(0).getChanges())
                .filter(changes -> !changes.isEmpty())
                .map(changes -> changes.get(0).getValue())
                .filter(this::isValidValue)
                .map(this::processValue)
                .orElseGet(() -> {
                    System.out.println(">>> Invalid or empty event payload");
                    return Response.ok("Ignored").build();
                });
    }

    private boolean isValidValue(Value value) {
        return value != null &&
                value.getMessages() != null && !value.getMessages().isEmpty() &&
                value.getContacts() != null && !value.getContacts().isEmpty();
    }

    private Response processValue(Value value) {

        Contact contact = value.getContacts().get(0);

        Message message = value.getMessages().get(0);

        String type = message.getType();

        String content = "";

        String buttonPayload = null;

        if ("text".equals(type) && message.getText() != null) {
            content = message.getText().getBody();
        }

        if ("button".equals(type) && message.getButton() != null) {

            content = message.getButton().getText();

            buttonPayload = message.getButton().getPayload();

        }

        String originalMessageId = Optional.ofNullable(message.getContext())
                .map(Context::getId)
                .orElse(null);

        MetaEventLog log = MetaEventLog.create(
                contact.getWaId(),
                message.getId(),
                originalMessageId,
                type,
                content,
                buttonPayload);

        try {

            log.rawPayload = mapper.writeValueAsString(value);

        } catch (JsonProcessingException e) {

            log.rawPayload = "{}";

        }

        log.persist().await().indefinitely();

        System.out.println("Saved inbound message.");

        if ("text".equals(type)) {

            String aiResponse = aiService.chat(content);

            log.aiResponse = aiResponse;

            log.status = "AI_PROCESSED";

            log.update().await().indefinitely();

            return Response.ok(
                    Map.of(
                            "message", "AI processed",
                            "response", aiResponse))
                    .build();
        }

        if ("button".equals(type)) {
            return processWithCdp(
                    log,
                    content,
                    buttonPayload);

        }

        return Response.ok().build();
    }

    private Response processWithCdp(

            MetaEventLog log,

            String buttonReply,

            String buttonPayload) {
        System.out.println(">>> Sending to CDP: " + buttonPayload);
        String originalWamId = log.originalMessageId;
        String customerId = extract(buttonPayload, "customerId");

        String templateName = extract(buttonPayload, "templatename");
        var payload = buildCdpPayload(

                customerId,

                templateName,

                log.messageId,

                originalWamId,

                buttonReply);
        try {

            log.cdpPayload = mapper.writeValueAsString(payload);
            System.out.println(
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(payload));
            cdpClient.sendEvent(payload).await().indefinitely();

            log.sentToCdpAt = Instant.now();

            log.status = "SENT_TO_CDP";

            log.update().await().indefinitely();

            return Response.ok(
                    Map.of(
                            "message",
                            "Sent to CDP"))
                    .build();

        } catch (Exception ex) {

            ex.printStackTrace();

            log.status = "CDP_ERROR";

            log.update().await().indefinitely();

            return Response.ok(
                    Map.of(
                            "message",
                            "CDP error"))
                    .build();

        }
    }

    private CdpModels.CdpPayload buildCdpPayload(

            String customerId,

            String templateName,

            String replyWamId,

            String originalWamId,

            String buttonReply) {

        var identity = new CdpModels.Identity(customerId);

        var feedback = new CdpModels.Feedback(
                buttonReply,
                "whatsapp",
                templateName,
                originalWamId,
                replyWamId);

        var customer = new CdpModels.Customer(feedback);

        var transientData = new CdpModels.Transient(customer);

        var bcp = new CdpModels.Bcp(
                identity,
                transientData);

        return new CdpModels.CdpPayload(

                UUID.randomUUID().toString(),

                "whatsapp.feedback.reply",

                Instant.now().toString(),

                bcp);

    }

    private String extract(String payload, String key) {

        if (payload == null) {
            return null;
        }

        for (String part : payload.split("\\|")) {

            if (part.startsWith(key + "=")) {

                return part.substring((key + "=").length());

            }

        }

        return null;
    }
}
