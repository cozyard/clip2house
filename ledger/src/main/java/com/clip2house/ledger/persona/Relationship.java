package com.clip2house.ledger.persona;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "relationships")
public class Relationship {

    public enum Outcome { NONE, TRADED, DECLINED, HIJACKED, EXPIRED }

    public enum Standing { FRIENDLY, NEUTRAL, WARY }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "player_id")
        private Long playerId;

        @Column(name = "persona_id")
        private Long personaId;

        protected Key() {}

        public Key(Long playerId, Long personaId) {
            this.playerId = playerId;
            this.personaId = personaId;
        }

        public Long getPlayerId() { return playerId; }
        public Long getPersonaId() { return personaId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(playerId, k.playerId) && Objects.equals(personaId, k.personaId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, personaId);
        }
    }

    @EmbeddedId
    private Key key;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_outcome", nullable = false)
    private Outcome lastOutcome = Outcome.NONE;

    @Column(name = "injection_attempted", nullable = false)
    private boolean injectionAttempted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Standing standing = Standing.NEUTRAL;

    @Column(name = "trades_count", nullable = false)
    private int tradesCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Relationship() {}

    public Relationship(Long playerId, Long personaId) {
        this.key = new Key(playerId, personaId);
    }

    public void recordTrade() {
        this.lastOutcome = Outcome.TRADED;
        this.tradesCount++;
        this.standing = Standing.FRIENDLY;
        this.updatedAt = Instant.now();
    }

    public void recordOutcome(Outcome outcome) {
        this.lastOutcome = outcome;
        this.updatedAt = Instant.now();
    }

    public void recordInjectionAttempt() {
        this.injectionAttempted = true;
        this.standing = Standing.WARY;
        this.updatedAt = Instant.now();
    }

    public Key getKey() { return key; }
    public Outcome getLastOutcome() { return lastOutcome; }
    public boolean isInjectionAttempted() { return injectionAttempted; }
    public Standing getStanding() { return standing; }
    public int getTradesCount() { return tradesCount; }
}
