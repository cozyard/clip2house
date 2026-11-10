package com.clip2house.ledger.negotiation;

import com.clip2house.ledger.auth.RateLimiter;
import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.internal.OrchestratorClient;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.market.ListingRepository;
import com.clip2house.ledger.observability.AnalyticsRecorder;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NegotiationService {

    private final NegotiationRepository negotiations;
    private final ListingRepository listings;
    private final HoldingRepository holdings;
    private final ChatMessageRepository messages;
    private final OfferRepository offers;
    private final RateLimiter rateLimiter;
    private final OrchestratorClient orchestrator;
    private final AnalyticsRecorder analytics;
    private final NegotiationEventBus eventBus;

    public NegotiationService(NegotiationRepository negotiations, ListingRepository listings,
                              HoldingRepository holdings, ChatMessageRepository messages, OfferRepository offers,
                              RateLimiter rateLimiter, OrchestratorClient orchestrator, AnalyticsRecorder analytics,
                              NegotiationEventBus eventBus) {
        this.negotiations = negotiations;
        this.listings = listings;
        this.holdings = holdings;
        this.messages = messages;
        this.offers = offers;
        this.rateLimiter = rateLimiter;
        this.orchestrator = orchestrator;
        this.analytics = analytics;
        this.eventBus = eventBus;
    }

    @Transactional
    public Negotiation open(Long playerId, Long listingId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("LISTING_NOT_FOUND", "매물이 없습니다."));
        if (listing.getStatus() != Listing.Status.ACTIVE) {
            throw ApiException.conflict("LISTING_GONE", "이미 거래되었거나 내려간 매물입니다.");
        }
        return negotiations.findByListingIdAndPlayerIdAndStatus(listingId, playerId, Negotiation.Status.ACTIVE)
                .orElseGet(() -> negotiations.save(new Negotiation(listingId, playerId)));
    }

    @Transactional
    public ChatMessage postMessage(Long playerId, Long negotiationId, String content) {
        Negotiation negotiation = ownedActive(playerId, negotiationId);
        if (!rateLimiter.allowMessage(playerId)) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED", "메시지가 너무 잦습니다. 잠시 후 다시 보내세요.");
        }
        ChatMessage saved = messages.save(new ChatMessage(negotiationId, ChatMessage.Sender.PLAYER, content));

        notifyPersonaTurn(negotiation);
        eventBus.publish(negotiationId);
        return saved;
    }

    @Transactional
    public Offer placeOffer(Long playerId, Long negotiationId, Long holdingId) {
        Negotiation negotiation = ownedActive(playerId, negotiationId);
        Holding holding = holdings.findById(holdingId)
                .filter(h -> h.getPlayerId().equals(playerId) && h.getStatus() == Holding.Status.ACTIVE)
                .orElseThrow(() -> ApiException.badRequest("HOLDING_UNAVAILABLE", "보유 중인 물건이 아닙니다."));

        offers.findByNegotiationIdAndProposerAndStatus(negotiationId, Offer.Proposer.PLAYER, Offer.Status.STANDING)
                .ifPresent(standing -> standing.resolve(Offer.Status.SUPERSEDED));
        Offer offer = offers.save(new Offer(negotiationId, Offer.Proposer.PLAYER, holding.getId()));
        analytics.record(AnalyticsRecorder.OFFER, playerId,
                Map.of("negotiationId", negotiationId, "offerId", offer.getId(), "proposer", "PLAYER"));
        notifyPersonaTurn(negotiation);
        eventBus.publish(negotiationId);
        return offer;
    }

    @Transactional
    public void declineOffer(Long playerId, Long negotiationId, Long offerId) {
        Negotiation negotiation = ownedActive(playerId, negotiationId);
        Offer offer = offers.findById(offerId)
                .filter(o -> o.getNegotiationId().equals(negotiationId) && o.isStanding()
                             && o.getProposer() == Offer.Proposer.PERSONA)
                .orElseThrow(() -> ApiException.conflict("OFFER_NOT_STANDING", "거절할 수 있는 제안이 아닙니다."));
        offer.resolve(Offer.Status.DECLINED);
        analytics.record(AnalyticsRecorder.DECLINE, playerId,
                Map.of("negotiationId", negotiationId, "offerId", offerId, "declinedBy", "PLAYER"));
        notifyPersonaTurn(negotiation);
        eventBus.publish(negotiationId);
    }

    private void notifyPersonaTurn(Negotiation negotiation) {
        listings.findById(negotiation.getListingId())
                .ifPresent(listing -> orchestrator.notifyTurn(negotiation.getId(), listing.getPersonaId()));
    }

    private Negotiation ownedActive(Long playerId, Long negotiationId) {
        Negotiation negotiation = negotiations.findById(negotiationId)
                .orElseThrow(() -> ApiException.notFound("NEGOTIATION_NOT_FOUND", "협상이 없습니다."));
        if (!negotiation.getPlayerId().equals(playerId)) {
            throw ApiException.notFound("NEGOTIATION_NOT_FOUND", "협상이 없습니다.");
        }
        if (!negotiation.isActive()) {

            throw ApiException.conflict("NEGOTIATION_CLOSED", closedMessage(negotiation));
        }
        return negotiation;
    }

    static String closedMessage(Negotiation negotiation) {
        if (negotiation.getStatus() == Negotiation.Status.COMPLETED) {
            return "이미 성사된 협상입니다.";
        }
        if (negotiation.getInvalidationReason() == null) {
            return "협상이 종료되었습니다.";
        }
        return switch (negotiation.getInvalidationReason()) {
            case HIJACKED -> "이미 거래된 매물입니다. 다른 사람이 먼저 성사시켰습니다.";
            case LISTING_EXPIRED -> "매물이 내려가 협상이 종료되었습니다.";
            case VERSION_CONFLICT -> "협상이 종료되었습니다.";
        };
    }
}
