package com.clip2house.ledger.market;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListingRepository extends JpaRepository<Listing, Long> {

    long countByStatus(Listing.Status status);

    List<Listing> findTop100ByStatusOrderByCreatedAtDesc(Listing.Status status);

    List<Listing> findByStatusAndExpiresAtBefore(Listing.Status status, Instant now);

    @Modifying
    @Query("""
            update Listing l set l.status = 'CONSUMED', l.version = l.version + 1
            where l.id = :id and l.version = :expectedVersion and l.status = 'ACTIVE'
            """)
    int consumeIfVersionMatches(@Param("id") Long id, @Param("expectedVersion") long expectedVersion);

    @Modifying
    @Query("""
            update Listing l set l.status = 'EXPIRED', l.version = l.version + 1
            where l.id = :id and l.status = 'ACTIVE'
            """)
    int expire(@Param("id") Long id);
}
