package com.clip2house.ledger.negotiation;

import com.clip2house.ledger.observability.AnalyticsRecorder;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NegotiationInvalidator {

    private final NegotiationRepository negotiations;
    private final OfferRepository offers;
    private final AnalyticsRecorder analytics;
    private final NegotiationEventBus eventBus;

    public NegotiationInvalidator(NegotiationRepository negotiations, OfferRepository offers,
                                  AnalyticsRecorder analytics, NegotiationEventBus eventBus) {
        this.negotiations = negotiations;
        this.offers = offers;
        this.analytics = analytics;
        this.eventBus = eventBus;
    }

    public void invalidateActiveOnListing(Long listingId, Long exceptNegotiationId,
                                          Negotiation.InvalidationReason reason) {
        List<Negotiation> actives = negotiations.findByListingIdAndStatus(listingId, Negotiation.Status.ACTIVE);
        for (Negotiation negotiation : actives) {
            if (negotiation.getId().equals(exceptNegotiationId)) {
                continue;
            }
            negotiation.invalidate(reason);
            offers.findByNegotiationIdAndStatus(negotiation.getId(), Offer.Status.STANDING)
                    .forEach(offer -> offer.resolve(Offer.Status.INVALIDATED));
            if (reason == Negotiation.InvalidationReason.HIJACKED) {
                analytics.record(AnalyticsRecorder.HIJACK, negotiation.getPlayerId(),
                        Map.of("negotiationId", negotiation.getId(), "listingId", listingId));
            }

            eventBus.publish(negotiation.getId());
        }
    }
}
