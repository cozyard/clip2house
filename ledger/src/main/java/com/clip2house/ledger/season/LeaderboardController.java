package com.clip2house.ledger.season;

import com.clip2house.ledger.auth.PlayerRepository;
import com.clip2house.ledger.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    public enum Board { VALUE, CLEAR_ORDER, CLEAR_TIME, CLEAR_TRADES }

    private final SeasonService seasonService;
    private final SeasonPlayerRepository seasonPlayers;
    private final PlayerRepository players;

    public LeaderboardController(SeasonService seasonService, SeasonPlayerRepository seasonPlayers,
                                 PlayerRepository players) {
        this.seasonService = seasonService;
        this.seasonPlayers = seasonPlayers;
        this.players = players;
    }

    public record Entry(int rank, String nickname, boolean cleared, int tradesCount, Long clearDurationSecs) {}

    public record LeaderboardResponse(Long seasonId, String board, List<Entry> entries) {}

    @GetMapping
    public LeaderboardResponse leaderboard(@RequestParam(defaultValue = "VALUE") String board) {
        Board selected;
        try {
            selected = Board.valueOf(board.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("UNKNOWN_BOARD", "지원하지 않는 보드입니다: " + board);
        }

        Long seasonId = seasonService.current().getId();
        List<SeasonPlayer> rows = switch (selected) {
            case VALUE -> seasonPlayers.findTop100ByKeySeasonIdOrderByInventoryValueDesc(seasonId);
            case CLEAR_ORDER -> seasonPlayers.findTop100ByKeySeasonIdAndClearRankNotNullOrderByClearRankAsc(seasonId);
            case CLEAR_TIME ->
                    seasonPlayers.findTop100ByKeySeasonIdAndClearDurationSecsNotNullOrderByClearDurationSecsAsc(seasonId);
            case CLEAR_TRADES ->
                    seasonPlayers.findTop100ByKeySeasonIdAndTradesAtClearNotNullOrderByTradesAtClearAsc(seasonId);
        };

        Map<Long, String> nicknames = players.findAllById(
                        rows.stream().map(r -> r.getKey().getPlayerId()).toList()).stream()
                .collect(Collectors.toMap(p -> p.getId(), p -> p.getNickname()));

        List<Entry> entries = rows.stream()
                .map(withIndex(row -> new Entry(0, nicknames.get(row.getKey().getPlayerId()), row.isCleared(),
                        row.getTradesCount(), row.getClearDurationSecs())))
                .toList();
        return new LeaderboardResponse(seasonId, selected.name(), entries);
    }

    private static Function<SeasonPlayer, Entry> withIndex(Function<SeasonPlayer, Entry> mapper) {
        int[] counter = {0};
        return row -> {
            Entry base = mapper.apply(row);
            counter[0]++;
            return new Entry(counter[0], base.nickname(), base.cleared(), base.tradesCount(), base.clearDurationSecs());
        };
    }
}
