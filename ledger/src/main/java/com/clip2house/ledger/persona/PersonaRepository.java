package com.clip2house.ledger.persona;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    List<Persona> findByActiveTrue();

    @Query(value = "SELECT * FROM personas WHERE active ORDER BY random() LIMIT 1", nativeQuery = true)
    Persona pickRandomActive();

    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE personas p
            SET avg_response_seconds = s.avg_secs
            FROM (
                SELECT l.persona_id,
                       CAST(ROUND(AVG(EXTRACT(EPOCH FROM (m.next_at - m.created_at)))) AS INT) AS avg_secs
                FROM (
                    SELECT negotiation_id, sender, created_at,
                           LEAD(sender) OVER w AS next_sender,
                           LEAD(created_at) OVER w AS next_at
                    FROM chat_messages
                    WHERE created_at > :since
                    WINDOW w AS (PARTITION BY negotiation_id ORDER BY id)
                ) m
                JOIN negotiations n ON n.id = m.negotiation_id
                JOIN listings l ON l.id = n.listing_id
                WHERE m.sender = 'PLAYER' AND m.next_sender = 'PERSONA'
                GROUP BY l.persona_id
            ) s
            WHERE p.id = s.persona_id
            """, nativeQuery = true)
    int refreshAvgResponseSeconds(@Param("since") Instant since);
}
