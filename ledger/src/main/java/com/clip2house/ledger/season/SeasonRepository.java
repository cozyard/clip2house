package com.clip2house.ledger.season;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    @Query("select s from Season s where s.startsAt <= :now and s.endsAt > :now order by s.id desc limit 1")
    Optional<Season> findCurrent(@Param("now") Instant now);

    Optional<Season> findTopByOrderByEndsAtDesc();
}
