package com.clip2house.ledger.market;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByPlayerIdAndStatus(Long playerId, Holding.Status status);

    @Modifying
    @Query("""
            update Holding h set h.status = 'TRADED_AWAY', h.version = h.version + 1, h.releasedAt = CURRENT_TIMESTAMP
            where h.id = :id and h.version = :expectedVersion and h.status = 'ACTIVE'
            """)
    int releaseIfVersionMatches(@Param("id") Long id, @Param("expectedVersion") long expectedVersion);

    @Query("""
            select coalesce(sum(coalesce(i.correctedValue, i.refValue)), 0)
            from Holding h join Item i on h.itemId = i.id
            where h.playerId = :playerId and h.status = 'ACTIVE'
            """)
    BigDecimal sumActiveInventoryValue(@Param("playerId") Long playerId);
}
