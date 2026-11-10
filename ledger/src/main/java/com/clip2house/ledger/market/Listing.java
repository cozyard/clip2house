package com.clip2house.ledger.market;

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
@Table(name = "listings")
public class Listing {

    public enum Origin { FAUCET, RECYCLED }

    public enum Status { ACTIVE, CONSUMED, EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private long version;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected Listing() {}

    public Listing(Long itemId, Long personaId, Origin origin, Instant expiresAt) {
        this.itemId = itemId;
        this.personaId = personaId;
        this.origin = origin;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getItemId() { return itemId; }
    public Long getPersonaId() { return personaId; }
    public Origin getOrigin() { return origin; }
    public Status getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
