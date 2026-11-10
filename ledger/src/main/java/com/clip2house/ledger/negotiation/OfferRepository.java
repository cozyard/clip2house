package com.clip2house.ledger.negotiation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByNegotiationIdAndStatus(Long negotiationId, Offer.Status status);

    Optional<Offer> findByNegotiationIdAndProposerAndStatus(Long negotiationId, Offer.Proposer proposer, Offer.Status status);
}
