package com.clip2house.ledger.auth;

import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.season.SeasonPlayer;
import com.clip2house.ledger.season.SeasonService;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final PlayerRepository players;
    private final HoldingRepository holdings;
    private final ItemRepository items;
    private final SeasonService seasonService;

    public MeController(PlayerRepository players, HoldingRepository holdings, ItemRepository items,
                        SeasonService seasonService) {
        this.players = players;
        this.holdings = holdings;
        this.items = items;
        this.seasonService = seasonService;
    }

    public record HoldingView(Long holdingId, long version, String itemName, String description, Instant acquiredAt) {}

    public record MeResponse(String nickname, boolean promoted, boolean cleared, Integer clearRank,
                             int tradesCount, List<HoldingView> holdings) {}

    @GetMapping
    public MeResponse me(@RequestAttribute("playerId") Long playerId) {
        Player player = players.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "플레이어가 없습니다."));
        Long seasonId = seasonService.current().getId();
        SeasonPlayer season = seasonService.ensureParticipant(seasonId, playerId);
        List<HoldingView> views = holdings.findByPlayerIdAndStatus(playerId, Holding.Status.ACTIVE).stream()
                .map(h -> {
                    var item = items.findById(h.getItemId()).orElseThrow();
                    return new HoldingView(h.getId(), h.getVersion(), item.getName(), item.getDescription(), h.getAcquiredAt());
                })
                .toList();
        return new MeResponse(player.getNickname(), player.isPromoted(), season.isCleared(),
                season.getClearRank(), season.getTradesCount(), views);
    }
}
