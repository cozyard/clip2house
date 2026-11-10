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
@Table(name = "negotiations")
public class Negotiation {

    public enum Status { ACTIVE, COMPLETED, INVALIDATED }

    public enum InvalidationReason { HIJACKED, LISTING_EXPIRED, VERSION_CONFLICT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "invalidation_reason")
    private InvalidationReason invalidationReason;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected Negotiation() {}

    public Negotiation(Long listingId, Long playerId) {
        this.listingId = listingId;
        this.playerId = playerId;
    }

    public void complete() {
        this.status = Status.COMPLETED;
        this.closedAt = Instant.now();
    }

    public void invalidate(InvalidationReason reason) {
        this.status = Status.INVALIDATED;
        this.invalidationReason = reason;
        this.closedAt = Instant.now();
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public Long getPlayerId() { return playerId; }
    public Status getStatus() { return status; }
    public InvalidationReason getInvalidationReason() { return invalidationReason; }
    public Instant getCreatedAt() { return createdAt; }
}
