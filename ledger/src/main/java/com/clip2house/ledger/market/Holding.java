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
@Table(name = "holdings")
public class Holding {

    public enum Status { ACTIVE, TRADED_AWAY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(nullable = false)
    private long version;

    @Column(name = "acquired_at", nullable = false, updatable = false, insertable = false)
    private Instant acquiredAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected Holding() {}

    public Holding(Long playerId, Long itemId) {
        this.playerId = playerId;
        this.itemId = itemId;
    }

    public Long getId() { return id; }
    public Long getPlayerId() { return playerId; }
    public Long getItemId() { return itemId; }
    public Status getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getAcquiredAt() { return acquiredAt; }
}
