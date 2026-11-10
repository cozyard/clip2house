package com.clip2house.ledger.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {

    Optional<Item> findByNameKey(String nameKey);

    List<Item> findTop200ByOrderByIdDesc();

    List<Item> findByGenVersion(String genVersion);

    @Query(value = """
            SELECT * FROM items
            WHERE validation_status = 'VALID'
            ORDER BY ABS(LOG(COALESCE(corrected_value, ref_value)) - :targetLog)
            LIMIT 1
            """, nativeQuery = true)
    Optional<Item> findNearestValid(@Param("targetLog") double targetLog);
}
