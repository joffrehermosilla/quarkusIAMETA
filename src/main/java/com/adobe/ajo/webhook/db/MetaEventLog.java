package com.adobe.ajo.webhook.db;

import io.quarkus.mongodb.panache.reactive.ReactivePanacheMongoEntity;
import java.time.Instant;

public class MetaEventLog extends ReactivePanacheMongoEntity {
    public String waId;
    public String messageId;
    public String type;
    public String content;
    public Instant timestamp;

    public static MetaEventLog create(String waId, String messageId, String type, String content) {
        MetaEventLog log = new MetaEventLog();
        log.waId = waId;
        log.messageId = messageId;
        log.type = type;
        log.content = content;
        log.timestamp = Instant.now();
        return log;
    }
}
