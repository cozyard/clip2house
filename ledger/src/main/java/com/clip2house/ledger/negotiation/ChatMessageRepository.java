package com.clip2house.ledger.negotiation;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByNegotiationIdOrderByIdAsc(Long negotiationId);

    @Query("""
            select m from ChatMessage m
            where m.negotiationId = :negotiationId and m.sender = 'PLAYER'
              and m.id > coalesce((select max(p.id) from ChatMessage p
                                   where p.negotiationId = :negotiationId and p.sender = 'PERSONA'), 0)
            order by m.id asc
            """)
    List<ChatMessage> findUnansweredPlayerMessages(@Param("negotiationId") Long negotiationId);

    @Modifying
    @Query("""
            update ChatMessage m set m.readAt = :now
            where m.negotiationId = :negotiationId and m.sender = 'PLAYER' and m.readAt is null
            """)
    int markPlayerMessagesRead(@Param("negotiationId") Long negotiationId, @Param("now") Instant now);
}
