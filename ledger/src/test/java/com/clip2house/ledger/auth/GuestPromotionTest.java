package com.clip2house.ledger.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.season.SeasonPlayer;
import com.clip2house.ledger.season.SeasonPlayerRepository;
import com.clip2house.ledger.season.SeasonService;
import com.clip2house.ledger.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GuestPromotionTest extends IntegrationTestBase {

    @Autowired TokenService tokenService;
    @Autowired SeasonService seasonService;
    @Autowired SeasonPlayerRepository seasonPlayers;

    @Test
    void 게스트_승격_후에도_내부_식별자와_인벤토리_랭킹이_그대로다() {
        Player guest = newGuest();
        Long playerId = guest.getId();
        List<Holding> holdingsBefore = holdings.findByPlayerIdAndStatus(playerId, Holding.Status.ACTIVE);
        Long seasonId = seasonService.current().getId();
        SeasonPlayer rankRowBefore = seasonPlayers.findById(new SeasonPlayer.Key(seasonId, playerId)).orElseThrow();

        String email = "player-" + UUID.randomUUID() + "@clip2house.dev";
        Player promoted = authService.promote(playerId, email, "correct-horse-battery");

        assertThat(promoted.getId()).isEqualTo(playerId);
        assertThat(promoted.isPromoted()).isTrue();

        List<Holding> holdingsAfter = holdings.findByPlayerIdAndStatus(playerId, Holding.Status.ACTIVE);
        assertThat(holdingsAfter).extracting(Holding::getId)
                .containsExactlyElementsOf(holdingsBefore.stream().map(Holding::getId).toList());

        SeasonPlayer rankRowAfter = seasonPlayers.findById(new SeasonPlayer.Key(seasonId, playerId)).orElseThrow();
        assertThat(rankRowAfter.getStartedAt()).isEqualTo(rankRowBefore.getStartedAt());

        var token = authService.login(email, "correct-horse-battery");
        assertThat(tokenService.resolvePlayerId(token.token())).isEqualTo(playerId);
    }
}
