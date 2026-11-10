package com.clip2house.ledger.season;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "season_players")
public class SeasonPlayer {

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "season_id")
        private Long seasonId;

        @Column(name = "player_id")
        private Long playerId;

        protected Key() {}

        public Key(Long seasonId, Long playerId) {
            this.seasonId = seasonId;
            this.playerId = playerId;
        }

        public Long getSeasonId() { return seasonId; }
        public Long getPlayerId() { return playerId; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(seasonId, k.seasonId) && Objects.equals(playerId, k.playerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(seasonId, playerId);
        }
    }

    @EmbeddedId
    private Key key;

    @Column(name = "inventory_value", nullable = false)
    private BigDecimal inventoryValue = BigDecimal.ZERO;

    @Column(name = "trades_count", nullable = false)
    private int tradesCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @Column(name = "clear_rank")
    private Integer clearRank;

    @Column(name = "clear_duration_secs")
    private Long clearDurationSecs;

    @Column(name = "trades_at_clear")
    private Integer tradesAtClear;

    protected SeasonPlayer() {}

    public SeasonPlayer(Long seasonId, Long playerId) {
        this.key = new Key(seasonId, playerId);
    }

    public void recordTrade(BigDecimal newInventoryValue) {
        this.tradesCount++;
        this.inventoryValue = newInventoryValue;
    }

    public void updateInventoryValue(BigDecimal newInventoryValue) {
        this.inventoryValue = newInventoryValue;
    }

    public void markCleared(int rank) {
        Instant now = Instant.now();
        this.clearedAt = now;
        this.clearRank = rank;
        this.clearDurationSecs = Duration.between(startedAt, now).getSeconds();
        this.tradesAtClear = tradesCount;
    }

    public boolean isCleared() {
        return clearedAt != null;
    }

    public Key getKey() { return key; }
    public BigDecimal getInventoryValue() { return inventoryValue; }
    public int getTradesCount() { return tradesCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getClearedAt() { return clearedAt; }
    public Integer getClearRank() { return clearRank; }
    public Long getClearDurationSecs() { return clearDurationSecs; }
    public Integer getTradesAtClear() { return tradesAtClear; }
}
