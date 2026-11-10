package com.clip2house.ledger.season;

import com.clip2house.ledger.config.GameProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonService {

    private static final Logger log = LoggerFactory.getLogger(SeasonService.class);

    private final SeasonRepository seasons;
    private final SeasonPlayerRepository seasonPlayers;
    private final GameProperties props;

    public SeasonService(SeasonRepository seasons, SeasonPlayerRepository seasonPlayers, GameProperties props) {
        this.seasons = seasons;
        this.seasonPlayers = seasonPlayers;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Season current() {
        return seasons.findCurrent(Instant.now())
                .orElseThrow(() -> new IllegalStateException("활성 시즌이 없습니다. 시즌 롤오버를 확인하세요."));
    }

    @Transactional
    public SeasonPlayer ensureParticipant(Long seasonId, Long playerId) {
        return seasonPlayers.findById(new SeasonPlayer.Key(seasonId, playerId))
                .orElseGet(() -> seasonPlayers.save(new SeasonPlayer(seasonId, playerId)));
    }

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void rolloverIfEnded() {
        Season latest = seasons.findTopByOrderByEndsAtDesc().orElse(null);
        Instant now = Instant.now();
        if (latest == null || !latest.getEndsAt().isAfter(now)) {

            Season next = seasons.save(new Season(now, now.plus(props.season().length())));
            log.info("새 시즌 시작: id={}, {} ~ {}", next.getId(), next.getStartsAt(), next.getEndsAt());
        }
    }
}
