package com.clip2house.ledger.negotiation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "offers")
public class Offer {

    public enum Proposer { PLAYER, PERSONA }

    public enum Status { STANDING, SUPERSEDED, ACCEPTED, DECLINED, INVALIDATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Proposer proposer;

    @Column(name = "player_holding_id", nullable = false)
    private Long playerHoldingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.STANDING;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Offer() {}

    public Offer(Long negotiationId, Proposer proposer, Long playerHoldingId) {
        this.negotiationId = negotiationId;
        this.proposer = proposer;
        this.playerHoldingId = playerHoldingId;
    }

    public boolean isStanding() {
        return status == Status.STANDING;
    }

    public void resolve(Status terminal) {
        this.status = terminal;
        this.resolvedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getNegotiationId() { return negotiationId; }
    public Proposer getProposer() { return proposer; }
    public Long getPlayerHoldingId() { return playerHoldingId; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
