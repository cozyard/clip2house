package com.clip2house.ledger.internal;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/messages")
public class InternalConsistencyController {

    private final JdbcClient jdbc;

    public InternalConsistencyController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/persona-samples")
    public List<Map<String, Object>> personaSamples(@RequestParam(defaultValue = "300") int limit) {
        return jdbc.sql("""
                        SELECT l.persona_id AS "personaId", m.content AS "content"
                        FROM chat_messages m
                        JOIN negotiations n ON n.id = m.negotiation_id
                        JOIN listings l ON l.id = n.listing_id
                        WHERE m.sender = 'PERSONA'
                        ORDER BY m.id DESC
                        LIMIT :limit
                        """)
                .param("limit", Math.min(limit, 1000))
                .query()
                .listOfRows();
    }
}
