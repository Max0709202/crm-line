package com.crm.line.dto;

import java.util.List;

/**
 * Shape of LINE's webhook request body. Field names match LINE's JSON exactly via
 * Jackson's default binding. Only the subset this integration currently handles is
 * modeled (text messages, follow/unfollow) — other event/message types deserialize with
 * their type field intact and get skipped explicitly rather than failing, so adding
 * support for a new type later is additive.
 */
public class LineWebhookPayload {

    private String destination;
    private List<LineEvent> events;

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }
    public List<LineEvent> getEvents() { return events; }
    public void setEvents(List<LineEvent> events) { this.events = events; }

    public static class LineEvent {
        private String type;
        private String webhookEventId;
        private String replyToken;
        private Long timestamp;
        private LineSource source;
        private LineMessageContent message;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getWebhookEventId() { return webhookEventId; }
        public void setWebhookEventId(String webhookEventId) { this.webhookEventId = webhookEventId; }
        public String getReplyToken() { return replyToken; }
        public void setReplyToken(String replyToken) { this.replyToken = replyToken; }
        public Long getTimestamp() { return timestamp; }
        public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
        public LineSource getSource() { return source; }
        public void setSource(LineSource source) { this.source = source; }
        public LineMessageContent getMessage() { return message; }
        public void setMessage(LineMessageContent message) { this.message = message; }
    }

    public static class LineSource {
        private String type;
        private String userId;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
    }

    public static class LineMessageContent {
        private String type;
        private String id;
        private String text;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}
