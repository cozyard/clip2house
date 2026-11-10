package com.clip2house.ledger.persona;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "personas")
public class Persona {

    @Id
    private Long id;

    @Column(nullable = false)
    private String nickname;

    private String community;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "avg_response_seconds")
    private Integer avgResponseSeconds;

    protected Persona() {}

    public Persona(Long id, String nickname, String community) {
        this.id = id;
        this.nickname = nickname;
        this.community = community;
    }

    public void update(String nickname, String community, boolean active) {
        this.nickname = nickname;
        this.community = community;
        this.active = active;
    }

    public Long getId() { return id; }
    public String getNickname() { return nickname; }
    public String getCommunity() { return community; }
    public boolean isActive() { return active; }
    public Integer getAvgResponseSeconds() { return avgResponseSeconds; }
}
