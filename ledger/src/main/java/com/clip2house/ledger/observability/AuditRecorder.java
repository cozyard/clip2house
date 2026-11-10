package com.clip2house.ledger.observability;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AuditRecorder {

    private final JdbcClient jdbc;

    public AuditRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void recordInjection(Long playerId, Long personaId, Long negotiationId,
                                String rawInput, String classifiedType, String botResponse) {
        jdbc.sql("""
                        INSERT INTO injection_audit (player_id, persona_id, negotiation_id, raw_input, classified_type, bot_response)
                        VALUES (:playerId, :personaId, :negotiationId, :rawInput, :classifiedType, :botResponse)
                        """)
                .param("playerId", playerId)
                .param("personaId", personaId)
                .param("negotiationId", negotiationId)
                .param("rawInput", rawInput)
                .param("classifiedType", classifiedType)
                .param("botResponse", botResponse)
                .update();
    }
}
