package com.clip2house.ledger.internal;

import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.negotiation.ChatMessage;
import com.clip2house.ledger.negotiation.ChatMessageRepository;
import com.clip2house.ledger.negotiation.Negotiation;
import com.clip2house.ledger.negotiation.NegotiationEventBus;
import com.clip2house.ledger.negotiation.NegotiationRepository;
import com.clip2house.ledger.negotiation.Offer;
import com.clip2house.ledger.negotiation.OfferRepository;
import com.clip2house.ledger.observability.AnalyticsRecorder;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonaActionService {

    private final NegotiationRepository negotiations;
    private final ChatMessageRepository messages;
    private final OfferRepository offers;
    private final HoldingRepository holdings;
    private final AnalyticsRecorder analytics;
    private final NegotiationEventBus eventBus;

    public PersonaActionService(NegotiationRepository negotiations, ChatMessageRepository messages,
                                OfferRepository offers, HoldingRepository holdings, AnalyticsRecorder analytics,
                                NegotiationEventBus eventBus) {
        this.negotiations = negotiations;
        this.messages = messages;
        this.offers = offers;
        this.holdings = holdings;
        this.analytics = analytics;
        this.eventBus = eventBus;
    }

    @Transactional
    public Map<String, Object> applyContinue(Long negotiationId, List<String> texts) {
        requireActive(negotiationId);
        savePersonaMessages(negotiationId, texts);
        return Map.of("applied", "CONTINUE");
    }

    @Transactional
    public Map<String, Object> applyOffer(Long negotiationId, Long offerHoldingId, List<String> texts) {
        Negotiation negotiation = requireActive(negotiationId);
        if (offerHoldingId == null) {
            throw ApiException.badRequest("MISSING_HOLDING", "OFFER에는 offerHoldingId가 필요합니다.");
        }
        Holding holding = holdings.findById(offerHoldingId)
                .filter(h -> h.getPlayerId().equals(negotiation.getPlayerId())
                             && h.getStatus() == Holding.Status.ACTIVE)
                .orElseThrow(() -> ApiException.badRequest("HOLDING_UNAVAILABLE",
                        "플레이어가 보유 중인 물건이 아닙니다."));
        offers.findByNegotiationIdAndProposerAndStatus(negotiationId, Offer.Proposer.PERSONA, Offer.Status.STANDING)
                .ifPresent(standing -> standing.resolve(Offer.Status.SUPERSEDED));
        Offer offer = offers.save(new Offer(negotiationId, Offer.Proposer.PERSONA, holding.getId()));
        savePersonaMessages(negotiationId, texts);
        analytics.record(AnalyticsRecorder.OFFER, negotiation.getPlayerId(),
                Map.of("negotiationId", negotiationId, "offerId", offer.getId(), "proposer", "PERSONA"));
        return Map.of("applied", "OFFER", "offerId", offer.getId());
    }

    @Transactional
    public Map<String, Object> applyDecline(Long negotiationId, List<String> texts) {
        Negotiation negotiation = requireActive(negotiationId);
        offers.findByNegotiationIdAndProposerAndStatus(negotiationId, Offer.Proposer.PLAYER, Offer.Status.STANDING)
                .ifPresent(standing -> standing.resolve(Offer.Status.DECLINED));
        savePersonaMessages(negotiationId, texts);
        analytics.record(AnalyticsRecorder.DECLINE, negotiation.getPlayerId(),
                Map.of("negotiationId", negotiationId, "declinedBy", "PERSONA"));
        return Map.of("applied", "DECLINE");
    }

    @Transactional
    public void saveMessages(Long negotiationId, List<String> texts) {
        savePersonaMessages(negotiationId, texts);
    }

    private void savePersonaMessages(Long negotiationId, List<String> texts) {
        texts.forEach(text -> messages.save(new ChatMessage(negotiationId, ChatMessage.Sender.PERSONA, text)));
        eventBus.publish(negotiationId);
    }

    private Negotiation requireActive(Long negotiationId) {
        Negotiation negotiation = negotiations.findById(negotiationId)
                .orElseThrow(() -> ApiException.notFound("NEGOTIATION_NOT_FOUND", "협상이 없습니다."));
        if (!negotiation.isActive()) {
            throw ApiException.conflict("NEGOTIATION_CLOSED", "협상이 이미 종료되었습니다.");
        }
        return negotiation;
    }
}
