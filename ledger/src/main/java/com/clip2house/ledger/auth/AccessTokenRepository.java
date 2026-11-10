package com.clip2house.ledger.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccessTokenRepository extends JpaRepository<AccessToken, String> {

    @Modifying
    @Query("delete from AccessToken t where t.expiresAt < :now")
    int deleteExpired(Instant now);
}
