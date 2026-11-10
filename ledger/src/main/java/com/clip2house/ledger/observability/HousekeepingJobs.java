package com.clip2house.ledger.observability;

import com.clip2house.ledger.auth.AccessTokenRepository;
import com.clip2house.ledger.config.GameProperties;
import com.clip2house.ledger.negotiation.IdempotencyRepository;
import com.clip2house.ledger.persona.PersonaRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HousekeepingJobs {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingJobs.class);

    private final IdempotencyRepository idempotency;
    private final AccessTokenRepository tokens;
    private final PersonaRepository personas;
    private final GameProperties properties;

    public HousekeepingJobs(IdempotencyRepository idempotency, AccessTokenRepository tokens,
                            PersonaRepository personas, GameProperties properties) {
        this.idempotency = idempotency;
        this.tokens = tokens;
        this.personas = personas;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${clip2house.idempotency.cleanup-interval}")
    @Transactional
    public void cleanup() {
        int keys = idempotency.deleteExpired(Instant.now());
        int expiredTokens = tokens.deleteExpired(Instant.now());
        if (keys > 0 || expiredTokens > 0) {
            log.info("정리: 멱등 키 {}건, 만료 토큰 {}건", keys, expiredTokens);
        }
    }

    @Scheduled(fixedDelayString = "${clip2house.persona-stats.refresh-interval}")
    @Transactional
    public void refreshPersonaResponseTimes() {
        Instant since = Instant.now().minus(properties.personaStats().lookback());
        int updated = personas.refreshAvgResponseSeconds(since);
        if (updated > 0) {
            log.debug("페르소나 평균 응답시간 갱신: {}명", updated);
        }
    }
}
