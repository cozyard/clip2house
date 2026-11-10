package com.clip2house.ledger.negotiation;

import static org.assertj.core.api.Assertions.assertThat;

import com.clip2house.ledger.auth.Player;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.negotiation.TradeCommitService.AcceptCommand;
import com.clip2house.ledger.negotiation.TradeCommitService.AcceptResult;
import com.clip2house.ledger.negotiation.TradeCommitService.Acceptor;
import com.clip2house.ledger.support.IntegrationTestBase;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TradeCommitProtocolTest extends IntegrationTestBase {

    @Autowired TradeCommitService commitService;
    @Autowired TradeRepository trades;
    @Autowired JdbcTemplate jdbc;

    private AcceptResult accept(Negotiation negotiation, Offer offer, Listing listing, Holding holding,
                                Acceptor acceptor, String key) {
        return commitService.accept(new AcceptCommand(negotiation.getId(), offer.getId(),
                listing.getVersion(), holding.getVersion(), key, acceptor));
    }

    @Test
    void 같은_매물을_두_플레이어가_동시_수락하면_첫_커밋만_성공하고_패자는_하이재킹된다() throws Exception {
        Listing listing = newListing("wireless-mouse");
        Player p1 = newGuest();
        Player p2 = newGuest();
        Negotiation n1 = openNegotiation(p1.getId(), listing);
        Negotiation n2 = openNegotiation(p2.getId(), listing);
        Offer o1 = personaOffer(n1, activeHolding(p1.getId()));
        Offer o2 = personaOffer(n2, activeHolding(p2.getId()));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AcceptResult> f1 = pool.submit(() -> {
                start.await();
                return accept(n1, o1, listing, activeHolding(p1.getId()), Acceptor.PLAYER, UUID.randomUUID().toString());
            });
            Future<AcceptResult> f2 = pool.submit(() -> {
                start.await();
                return accept(n2, o2, listing, activeHolding(p2.getId()), Acceptor.PLAYER, UUID.randomUUID().toString());
            });
            start.countDown();
            List<AcceptResult> results = List.of(f1.get(), f2.get());

            assertThat(results.stream().filter(AcceptResult::success)).hasSize(1);
            assertThat(results.stream().filter(r -> r.httpStatus() == 409)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(trades.countByPlayerId(p1.getId()) + trades.countByPlayerId(p2.getId())).isEqualTo(1);
        List<Negotiation> all = negotiations.findAllById(List.of(n1.getId(), n2.getId()));
        assertThat(all).extracting(Negotiation::getStatus)
                .containsExactlyInAnyOrder(Negotiation.Status.COMPLETED, Negotiation.Status.INVALIDATED);
        Negotiation loser = all.stream().filter(n -> !n.isActive() && n.getStatus() == Negotiation.Status.INVALIDATED)
                .findFirst().orElseThrow();
        assertThat(loser.getInvalidationReason()).isEqualTo(Negotiation.InvalidationReason.HIJACKED);

        Integer hijacks = jdbc.queryForObject(
                "SELECT COUNT(*) FROM analytics_events WHERE event_type = 'HIJACK' AND player_id = ?",
                Integer.class, loser.getPlayerId());
        assertThat(hijacks).isEqualTo(1);
    }

    @Test
    void 같은_홀딩을_두_협상에_동시에_내밀면_첫_커밋만_성공한다() throws Exception {
        Player player = newGuest();
        Holding clip = activeHolding(player.getId());
        Listing l1 = newListing("steel-tumbler");
        Listing l2 = newListing("power-bank");
        Negotiation n1 = openNegotiation(player.getId(), l1);
        Negotiation n2 = openNegotiation(player.getId(), l2);
        Offer o1 = personaOffer(n1, clip);
        Offer o2 = personaOffer(n2, clip);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<AcceptResult> f1 = pool.submit(() -> {
                start.await();
                return accept(n1, o1, l1, clip, Acceptor.PLAYER, UUID.randomUUID().toString());
            });
            Future<AcceptResult> f2 = pool.submit(() -> {
                start.await();
                return accept(n2, o2, l2, clip, Acceptor.PLAYER, UUID.randomUUID().toString());
            });
            start.countDown();
            List<AcceptResult> results = List.of(f1.get(), f2.get());
            assertThat(results.stream().filter(AcceptResult::success)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(trades.countByPlayerId(player.getId())).isEqualTo(1);

        List<Listing> after = listings.findAllById(List.of(l1.getId(), l2.getId()));
        assertThat(after).extracting(Listing::getStatus)
                .containsExactlyInAnyOrder(Listing.Status.ACTIVE, Listing.Status.CONSUMED);
    }

    @Test
    void 기대_버전이_어긋난_수락은_무효다() {
        Listing listing = newListing("camping-chair");
        Player player = newGuest();
        Negotiation negotiation = openNegotiation(player.getId(), listing);
        Offer offer = personaOffer(negotiation, activeHolding(player.getId()));

        AcceptResult result = commitService.accept(new AcceptCommand(negotiation.getId(), offer.getId(),
                listing.getVersion() + 1, activeHolding(player.getId()).getVersion(),
                UUID.randomUUID().toString(), Acceptor.PLAYER));

        assertThat(result.httpStatus()).isEqualTo(409);
        assertThat(trades.countByPlayerId(player.getId())).isZero();
        assertThat(listings.findById(listing.getId()).orElseThrow().getStatus()).isEqualTo(Listing.Status.ACTIVE);
    }

    @Test
    void 같은_멱등키의_재요청은_거래를_늘리지_않고_동일_결과를_반환한다() {
        Listing listing = newListing("capsule-coffee-machine");
        Player player = newGuest();
        Holding clip = activeHolding(player.getId());
        Negotiation negotiation = openNegotiation(player.getId(), listing);
        Offer offer = personaOffer(negotiation, clip);
        String key = UUID.randomUUID().toString();

        AcceptResult first = accept(negotiation, offer, listing, clip, Acceptor.PLAYER, key);
        AcceptResult replay = accept(negotiation, offer, listing, clip, Acceptor.PLAYER, key);

        assertThat(first.success()).isTrue();
        assertThat(replay.httpStatus()).isEqualTo(first.httpStatus());

        assertThat(((Number) replay.body().get("tradeId")).longValue())
                .isEqualTo(((Number) first.body().get("tradeId")).longValue());
        assertThat(trades.countByPlayerId(player.getId())).isEqualTo(1);
    }

    @Test
    void 실패_결과도_멱등키에_저장되어_그대로_재생된다() {
        Listing listing = newListing("gaming-monitor");
        Player player = newGuest();
        Holding clip = activeHolding(player.getId());
        Negotiation negotiation = openNegotiation(player.getId(), listing);
        Offer offer = personaOffer(negotiation, clip);
        String key = UUID.randomUUID().toString();

        AcceptResult failed = commitService.accept(new AcceptCommand(negotiation.getId(), offer.getId(),
                listing.getVersion() + 99, clip.getVersion(), key, Acceptor.PLAYER));
        AcceptResult replay = commitService.accept(new AcceptCommand(negotiation.getId(), offer.getId(),
                listing.getVersion() + 99, clip.getVersion(), key, Acceptor.PLAYER));

        assertThat(failed.httpStatus()).isEqualTo(409);
        assertThat(replay.httpStatus()).isEqualTo(409);
        assertThat(replay.body()).isEqualTo(failed.body());
    }

    @Test
    void 페르소나의_수락도_같은_커밋_프로토콜을_지난다() {
        Listing listing = newListing("road-bike");
        Player player = newGuest();
        Holding clip = activeHolding(player.getId());
        Negotiation negotiation = openNegotiation(player.getId(), listing);
        Offer offer = playerOffer(negotiation, clip);

        AcceptResult result = accept(negotiation, offer, listing, clip, Acceptor.PERSONA, "turn-" + UUID.randomUUID());

        assertThat(result.success()).isTrue();
        assertThat(holdings.findByPlayerIdAndStatus(player.getId(), Holding.Status.ACTIVE))
                .hasSize(1)
                .allSatisfy(h -> assertThat(h.getItemId()).isEqualTo(listing.getItemId()));
    }
}
