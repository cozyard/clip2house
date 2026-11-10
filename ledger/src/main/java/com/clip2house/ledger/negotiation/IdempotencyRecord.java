package com.clip2house.ledger.negotiation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", nullable = false)
    private String responseBody;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String key, Long playerId, int httpStatus, String responseBody, Instant expiresAt) {
        this.key = key;
        this.playerId = playerId;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
        this.expiresAt = expiresAt;
    }

    public String getKey() { return key; }
    public Long getPlayerId() { return playerId; }
    public int getHttpStatus() { return httpStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getExpiresAt() { return expiresAt; }
}
