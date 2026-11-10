package com.clip2house.ledger.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "access_tokens")
public class AccessToken {

    public enum Kind { GUEST, ACCOUNT }

    @Id
    @Column(name = "token_hash")
    private String tokenHash;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected AccessToken() {}

    public AccessToken(String tokenHash, Long playerId, Kind kind, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.playerId = playerId;
        this.kind = kind;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public String getTokenHash() { return tokenHash; }
    public Long getPlayerId() { return playerId; }
    public Kind getKind() { return kind; }
    public Instant getExpiresAt() { return expiresAt; }
}
