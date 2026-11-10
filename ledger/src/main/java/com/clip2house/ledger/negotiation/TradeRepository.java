package com.clip2house.ledger.negotiation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    long countByPlayerId(Long playerId);
}
