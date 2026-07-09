package com.adobe.ajo.webhook.db;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;

import java.time.Instant;

public class MetaEventLog extends ReactivePanacheMongoEntity {

    public String waId;

    public String messageId;

    public String originalMessageId;

    public String type;

    public String content;

    public String direction;

    public String status;

    public String aiResponse;

    public String rawPayload;

    public String cdpPayload;

    public Instant createdAt;

    public Instant sentToCdpAt;

    public String source;

    public String buttonPayload;

    public static MetaEventLog create(
            String waId,
            String messageId,
            String originalMessageId,
            String type,
            String content,
            String buttonPayload) {

        MetaEventLog log = new MetaEventLog();

        log.waId = waId;
        log.messageId = messageId;
        log.originalMessageId = originalMessageId;
        log.type = type;
        log.content = content;

        log.direction = "INBOUND";
        log.status = "RECEIVED";
        log.source = "META";
        log.buttonPayload = buttonPayload;
        log.createdAt = Instant.now();

        return log;
    }
}