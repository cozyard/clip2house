package com.clip2house.ledger.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsRecorder {

    public static final String TRADE = "TRADE";
    public static final String OFFER = "OFFER";
    public static final String ACCEPT = "ACCEPT";
    public static final String DECLINE = "DECLINE";
    public static final String HIJACK = "HIJACK";
    public static final String OFFLINE_DEGRADE = "OFFLINE_DEGRADE";
    public static final String CLEAR = "CLEAR";
    public static final String CLASSIFIER_HIT = "CLASSIFIER_HIT";

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public AnalyticsRecorder(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(String eventType, Long playerId, Map<String, Object> payload) {
        jdbc.sql("INSERT INTO analytics_events (event_type, player_id, payload) VALUES (:type, :playerId, :payload::jsonb)")
                .param("type", eventType)
                .param("playerId", playerId)
                .param("payload", toJson(payload))
                .update();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("analytics payload 직렬화 실패", e);
        }
    }
}
