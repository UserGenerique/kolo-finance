package com.kolofinance.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GowaWebhookPayload {

    private String event;

    @JsonProperty("device_id")
    private String deviceId;

    private Payload payload;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private String id;
        private String from;

        @JsonProperty("chat_id")
        private String chatId;

        private String body;

        @JsonProperty("is_from_me")
        private boolean fromMe;

        @JsonProperty("from_name")
        private String fromName;
    }
}
