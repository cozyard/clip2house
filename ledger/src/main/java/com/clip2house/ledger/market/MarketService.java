package com.clip2house.ledger.market;

import com.clip2house.ledger.config.GameProperties;
import com.clip2house.ledger.negotiation.Negotiation;
import com.clip2house.ledger.negotiation.NegotiationInvalidator;
import com.clip2house.ledger.persona.PersonaRepository;
import java.time.Instant;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);

    private final ListingRepository listings;
    private final PersonaRepository personas;
    private final NegotiationInvalidator invalidator;
    private final GameProperties props;
    private final RandomGenerator random;

    public MarketService(ListingRepository listings, PersonaRepository personas,
                         NegotiationInvalidator invalidator, GameProperties props, RandomGenerator random) {
        this.listings = listings;
        this.personas = personas;
        this.invalidator = invalidator;
        this.props = props;
        this.random = random;
    }

    @Scheduled(fixedDelayString = "${clip2house.market.expiry-interval}")
    @Transactional
    public void expireListings() {
        var expired = listings.findByStatusAndExpiresAtBefore(Listing.Status.ACTIVE, Instant.now());
        for (Listing listing : expired) {

            if (listings.expire(listing.getId()) == 1) {
                invalidator.invalidateActiveOnListing(listing.getId(), null,
                        Negotiation.InvalidationReason.LISTING_EXPIRED);
            }
        }
        if (!expired.isEmpty()) {
            log.info("매물 TTL 만료 처리: {}건", expired.size());
        }
    }

    @Transactional
    public void maybeRecycle(Long itemId) {
        if (random.nextDouble() >= props.market().recycleProbability()) {
            return;
        }
        var persona = personas.pickRandomActive();
        if (persona == null) {
            return;
        }
        listings.save(new Listing(itemId, persona.getId(), Listing.Origin.RECYCLED,
                Instant.now().plus(props.market().listingTtl())));
    }
}
