package com.adobe.ajo.webhook;

import com.adobe.ajo.webhook.api.WebhookApi;
import com.adobe.ajo.webhook.ai.AiFallbackService;
import com.adobe.ajo.webhook.cdp.CdpClient;
import com.adobe.ajo.webhook.db.MetaEventLog;
import com.adobe.ajo.webhook.model.CdpModels;
import com.adobe.ajo.webhook.model.generated.*;

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
        var contact = value.getContacts().get(0);
        var message = value.getMessages().get(0);
        String messageType = message.getType();

        System.out.println(">>> Processing message from: " + contact.getWaId() + " type: " + messageType);

        String content = null;
        if ("button".equals(messageType) && message.getButton() != null) {
            content = message.getButton().getText();
        } else if ("text".equals(messageType) && message.getText() != null) {
            content = message.getText().getBody();
        }

        // 1. Guardar en MongoDB
        try {
            MetaEventLog.create(contact.getWaId(), message.getId(), messageType, content)
                .persist()
                .await().indefinitely();
            System.out.println(">>> Log saved to MongoDB");
        } catch (Exception e) {
            System.err.println("Error saving to DB: " + e.getMessage());
        }

        // 2. Regla de negocio
        if ("text".equals(messageType)) {
            String aiResponse = aiService.chat(content);
            System.out.println(">>> AI responded: " + aiResponse);
            return Response.ok(Map.of("message", "AI Processed", "ai_response", aiResponse)).build();
        } else if ("button".equals(messageType)) {
            return processWithCdp(contact.getWaId(), message, content);
        }
        
        return Response.ok(Map.of("message", "Ignored")).build();
    }

    private Response processWithCdp(String waId, Message message, String buttonReply) {
        System.out.println(">>> Sending to CDP: " + buttonReply);
        String originalWamId = Optional.ofNullable(message.getContext())
                .map(Context::getId)
                .orElse(null);

        var payload = buildCdpPayload(waId, message.getId(), originalWamId, buttonReply);

        try {
            cdpClient.sendEvent(payload).await().indefinitely();
            return Response.ok(Map.of("message", "Event sent to CDP")).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.ok(Map.of("message", "Event processed with CDP error")).build();
        }
    }

    private CdpModels.CdpPayload buildCdpPayload(String waId, String replyWamId, String originalWamId, String buttonReply) {
        var whatsappIdentity = new CdpModels.WhatsappIdentity(waId, true);
        var identityMap = new CdpModels.IdentityMap(List.of(whatsappIdentity));
        var feedback = new CdpModels.Feedback(buttonReply, "whatsapp", originalWamId, replyWamId);
        var customer = new CdpModels.Customer(feedback);
        var transientData = new CdpModels.Transient(customer);
        var bcp = new CdpModels.Bcp(identityMap, transientData);
        
        return new CdpModels.CdpPayload(bcp, UUID.randomUUID().toString(), Instant.now().toString());
    }
}
