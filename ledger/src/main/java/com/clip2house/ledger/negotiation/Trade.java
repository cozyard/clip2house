package com.clip2house.ledger.negotiation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "given_holding_id", nullable = false)
    private Long givenHoldingId;

    @Column(name = "received_holding_id", nullable = false)
    private Long receivedHoldingId;

    @Column(name = "executed_at", nullable = false, updatable = false, insertable = false)
    private Instant executedAt;

    @Column(name = "offer_proposer")
    private String offerProposer;

    @Column(name = "persona_value_ratio")
    private BigDecimal personaValueRatio;

    protected Trade() {}

    public Trade(Long negotiationId, Long playerId, Long listingId, Long givenHoldingId, Long receivedHoldingId,
                 String offerProposer, BigDecimal personaValueRatio) {
        this.negotiationId = negotiationId;
        this.playerId = playerId;
        this.listingId = listingId;
        this.givenHoldingId = givenHoldingId;
        this.receivedHoldingId = receivedHoldingId;
        this.offerProposer = offerProposer;
        this.personaValueRatio = personaValueRatio;
    }

    public Long getId() { return id; }
    public Long getNegotiationId() { return negotiationId; }
    public Long getPlayerId() { return playerId; }
    public Long getListingId() { return listingId; }
    public Long getGivenHoldingId() { return givenHoldingId; }
    public Long getReceivedHoldingId() { return receivedHoldingId; }
    public Instant getExecutedAt() { return executedAt; }
    public String getOfferProposer() { return offerProposer; }
    public BigDecimal getPersonaValueRatio() { return personaValueRatio; }
}
