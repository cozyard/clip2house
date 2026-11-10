package com.clip2house.ledger.season;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonPlayerRepository extends JpaRepository<SeasonPlayer, SeasonPlayer.Key> {

    List<SeasonPlayer> findTop100ByKeySeasonIdOrderByInventoryValueDesc(Long seasonId);

    List<SeasonPlayer> findTop100ByKeySeasonIdAndClearRankNotNullOrderByClearRankAsc(Long seasonId);

    List<SeasonPlayer> findTop100ByKeySeasonIdAndClearDurationSecsNotNullOrderByClearDurationSecsAsc(Long seasonId);

    List<SeasonPlayer> findTop100ByKeySeasonIdAndTradesAtClearNotNullOrderByTradesAtClearAsc(Long seasonId);

    @Query("select count(sp) from SeasonPlayer sp where sp.key.seasonId = :seasonId and sp.clearedAt is not null")
    int countCleared(@Param("seasonId") Long seasonId);

    @Query("""
            select count(sp) + 1 from SeasonPlayer sp
            where sp.key.seasonId = :seasonId and sp.inventoryValue > :value
            """)
    long rankByValue(@Param("seasonId") Long seasonId, @Param("value") java.math.BigDecimal value);
}
