package com.clip2house.ledger.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.clip2house.ledger.auth.Player;
import com.clip2house.ledger.catalog.GoldenAnchorRepository;
import com.clip2house.ledger.catalog.Item;
import com.clip2house.ledger.negotiation.Negotiation;
import com.clip2house.ledger.negotiation.Offer;
import com.clip2house.ledger.negotiation.TradeCommitService;
import com.clip2house.ledger.support.IntegrationTestBase;
import com.clip2house.ledger.support.TestRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MarketLifecycleTest extends IntegrationTestBase {

    @Autowired FaucetService faucet;
    @Autowired MarketService market;
    @Autowired TradeCommitService commitService;
    @Autowired RandomGenerator random;
    @Autowired GoldenAnchorRepository anchors;

    @AfterEach
    void unfixRandom() {
        ((TestRandom) random).unfix();
    }

    @Test
    void 파우셋은_목표_활성_매물_수까지만_생성하고_도달_후에는_발화하지_않는다() {
        newPersona();

        int target = 5;

        while (listings.countByStatus(Listing.Status.ACTIVE) < target) {
            assertThat(faucet.refill()).isPositive();
        }
        long reached = listings.countByStatus(Listing.Status.ACTIVE);

        assertThat(faucet.refill()).isZero();
        assertThat(listings.countByStatus(Listing.Status.ACTIVE)).isEqualTo(reached);
    }

    @Test
    void 매물_TTL_만료는_그_위의_협상을_무효화한다() {
        Listing listing = listings.save(new Listing(seedItem("used-scooter").getId(), newPersona().getId(),
                Listing.Origin.FAUCET, Instant.now().minusSeconds(60)));
        Player player = newGuest();
        Negotiation negotiation = openNegotiation(player.getId(), listing);
        Offer standing = personaOffer(negotiation, activeHolding(player.getId()));

        market.expireListings();

        assertThat(listings.findById(listing.getId()).orElseThrow().getStatus()).isEqualTo(Listing.Status.EXPIRED);
        Negotiation after = negotiations.findById(negotiation.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(Negotiation.Status.INVALIDATED);
        assertThat(after.getInvalidationReason()).isEqualTo(Negotiation.InvalidationReason.LISTING_EXPIRED);
        assertThat(offers.findById(standing.getId()).orElseThrow().getStatus()).isEqualTo(Offer.Status.INVALIDATED);
    }

    @Test
    void 내준_물건은_확률_p로만_재매물이_되고_재순환_매물도_TTL을_가진다() {

        ((TestRandom) random).fixNextDouble(0.99);
        Long sunkItemId = tradeOnceAndReturnGivenItemId();
        assertThat(findRecycledListings(sunkItemId)).isEmpty();

        ((TestRandom) random).fixNextDouble(0.01);
        Long recycledItemId = tradeOnceAndReturnGivenItemId();
        List<Listing> recycled = findRecycledListings(recycledItemId);
        assertThat(recycled).hasSize(1);
        assertThat(recycled.get(0).getOrigin()).isEqualTo(Listing.Origin.RECYCLED);
        assertThat(recycled.get(0).getExpiresAt()).isAfter(Instant.now());
    }

    private Long tradeOnceAndReturnGivenItemId() {
        Listing listing = newListing("latest-laptop");
        Player player = newGuest();

        var uniqueItem = items.save(new Item(
                "재순환검증물건-" + UUID.randomUUID(), "recycle-test-" + UUID.randomUUID(),
                "재순환 테스트용", krw(50000), "test-v1", "human", "test",
                anchors.findByName("백반 한 끼").orElseThrow().getId()));
        Holding given = holdings.save(new Holding(player.getId(), uniqueItem.getId()));
        Negotiation negotiation = openNegotiation(player.getId(), listing);
        Offer offer = personaOffer(negotiation, given);
        var result = commitService.accept(new TradeCommitService.AcceptCommand(negotiation.getId(), offer.getId(),
                listing.getVersion(), given.getVersion(), UUID.randomUUID().toString(),
                TradeCommitService.Acceptor.PLAYER));
        assertThat(result.success()).isTrue();
        return given.getItemId();
    }

    private List<Listing> findRecycledListings(Long itemId) {
        return listings.findAll().stream()
                .filter(l -> l.getItemId().equals(itemId) && l.getOrigin() == Listing.Origin.RECYCLED)
                .toList();
    }
}
