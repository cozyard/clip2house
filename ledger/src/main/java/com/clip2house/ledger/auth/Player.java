package com.clip2house.ledger.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    protected Player() {}

    public Player(String nickname) {
        this.nickname = nickname;
    }

    public void promote(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.promotedAt = Instant.now();
    }

    public boolean isPromoted() {
        return promotedAt != null;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPromotedAt() { return promotedAt; }
}
