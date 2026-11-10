package com.clip2house.ledger.support;

import com.clip2house.ledger.auth.AuthService;
import com.clip2house.ledger.auth.Player;
import com.clip2house.ledger.catalog.Item;
import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.market.ListingRepository;
import com.clip2house.ledger.negotiation.Negotiation;
import com.clip2house.ledger.negotiation.NegotiationRepository;
import com.clip2house.ledger.negotiation.Offer;
import com.clip2house.ledger.negotiation.OfferRepository;
import com.clip2house.ledger.persona.Persona;
import com.clip2house.ledger.persona.PersonaRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "clip2house.scheduling.enabled=false",

        "clip2house.market.dictionary-match-tolerance=10",
        "clip2house.market.target-active-listings=5",
        "clip2house.rate-limit.guest-per-ip-per-hour=1000",
        "clip2house.rate-limit.messages-per-player-per-minute=1000",
        "clip2house.retention.archive-dir=build/test-archive",
})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Import({EmbeddedPostgresConfig.class, TestBeansConfig.class})
public abstract class IntegrationTestBase {

    private static final AtomicLong PERSONA_SEQ = new AtomicLong(9000);

    @Autowired protected AuthService authService;
    @Autowired protected ItemRepository items;
    @Autowired protected ListingRepository listings;
    @Autowired protected HoldingRepository holdings;
    @Autowired protected NegotiationRepository negotiations;
    @Autowired protected OfferRepository offers;
    @Autowired protected PersonaRepository personas;

    protected Player newGuest() {
        return authService.createGuest().player();
    }

    protected Holding activeHolding(Long playerId) {
        return holdings.findByPlayerIdAndStatus(playerId, Holding.Status.ACTIVE).get(0);
    }

    protected Persona newPersona() {
        long id = PERSONA_SEQ.incrementAndGet();
        return personas.save(new Persona(id, "페르소나" + id, null));
    }

    protected Item seedItem(String nameKey) {
        return items.findByNameKey(nameKey).orElseThrow();
    }

    protected Listing newListing(String itemNameKey) {
        return listings.save(new Listing(seedItem(itemNameKey).getId(), newPersona().getId(),
                Listing.Origin.FAUCET, Instant.now().plus(Duration.ofHours(12))));
    }

    protected Negotiation openNegotiation(Long playerId, Listing listing) {
        return negotiations.save(new Negotiation(listing.getId(), playerId));
    }

    protected Offer personaOffer(Negotiation negotiation, Holding playerHolding) {
        return offers.save(new Offer(negotiation.getId(), Offer.Proposer.PERSONA, playerHolding.getId()));
    }

    protected Offer playerOffer(Negotiation negotiation, Holding playerHolding) {
        return offers.save(new Offer(negotiation.getId(), Offer.Proposer.PLAYER, playerHolding.getId()));
    }

    protected static BigDecimal krw(long amount) {
        return BigDecimal.valueOf(amount);
    }
}
