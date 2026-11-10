package com.clip2house.ledger.negotiation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NegotiationRepository extends JpaRepository<Negotiation, Long> {

    Optional<Negotiation> findByListingIdAndPlayerIdAndStatus(Long listingId, Long playerId, Negotiation.Status status);

    List<Negotiation> findByListingIdAndStatus(Long listingId, Negotiation.Status status);

    List<Negotiation> findByPlayerIdOrderByCreatedAtDesc(Long playerId);

    List<Negotiation> findByPlayerIdAndStatusOrderByCreatedAtDesc(Long playerId, Negotiation.Status status);

    @Query("""
            select n from Negotiation n
            where n.status = :active
              and exists (select 1 from ChatMessage m
                          where m.negotiationId = n.id and m.sender = 'PLAYER'
                            and m.id > coalesce((select max(p.id) from ChatMessage p
                                                 where p.negotiationId = n.id and p.sender = 'PERSONA'), 0))
              and (select max(pm.createdAt) from ChatMessage pm
                   where pm.negotiationId = n.id and pm.sender = 'PLAYER') < :cutoff
            """)
    List<Negotiation> findStrandedAwaitingPersona(@Param("active") Negotiation.Status active,
                                                  @Param("cutoff") Instant cutoff);
}
