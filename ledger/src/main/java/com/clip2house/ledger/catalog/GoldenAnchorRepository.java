package com.clip2house.ledger.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoldenAnchorRepository extends JpaRepository<GoldenAnchor, Long> {

    Optional<GoldenAnchor> findByName(String name);
}
