package com.clip2house.ledger.negotiation;

import com.clip2house.ledger.catalog.Item;
import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.config.GameProperties;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.market.ListingRepository;
import com.clip2house.ledger.market.MarketService;
import com.clip2house.ledger.observability.AnalyticsRecorder;
import com.clip2house.ledger.persona.Relationship;
import com.clip2house.ledger.persona.RelationshipRepository;
import com.clip2house.ledger.season.SeasonPlayer;
import com.clip2house.ledger.season.SeasonPlayerRepository;
import com.clip2house.ledger.season.SeasonService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TradeCommitService {

    private static final Logger log = LoggerFactory.getLogger(TradeCommitService.class);

    public enum Acceptor { PLAYER, PERSONA }

    public record AcceptCommand(Long negotiationId, Long offerId, long expectedListingVersion,
                                long expectedHoldingVersion, String idempotencyKey, Acceptor acceptor) {}

    public record AcceptResult(int httpStatus, Map<String, Object> body) {
        public boolean success() {
            return httpStatus >= 200 && httpStatus < 300;
        }
    }

    private static class CommitConflict extends RuntimeException {
        final Negotiation.InvalidationReason reason;
        final String code;
        final String message;

        CommitConflict(Negotiation.InvalidationReason reason, String code, String message) {
            super(message);
            this.reason = reason;
            this.code = code;
            this.message = message;
        }
    }

    private final NegotiationRepository negotiations;
    private final OfferRepository offers;
    private final ListingRepository listings;
    private final HoldingRepository holdings;
    private final ItemRepository items;
    private final TradeRepository trades;
    private final IdempotencyRepository idempotency;
    private final NegotiationInvalidator invalidator;
    private final MarketService marketService;
    private final SeasonService seasonService;
    private final SeasonPlayerRepository seasonPlayers;
    private final RelationshipRepository relationships;
    private final AnalyticsRecorder analytics;
    private final GameProperties props;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final NegotiationEventBus eventBus;
    private final MeterRegistry meterRegistry;

    public TradeCommitService(NegotiationRepository negotiations, OfferRepository offers,
                              ListingRepository listings, HoldingRepository holdings, ItemRepository items,
                              TradeRepository trades, IdempotencyRepository idempotency,
                              NegotiationInvalidator invalidator, MarketService marketService,
                              SeasonService seasonService, SeasonPlayerRepository seasonPlayers,
                              RelationshipRepository relationships, AnalyticsRecorder analytics,
                              GameProperties props, TransactionTemplate tx, JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper, NegotiationEventBus eventBus, MeterRegistry meterRegistry) {
        this.negotiations = negotiations;
        this.offers = offers;
        this.listings = listings;
        this.holdings = holdings;
        this.items = items;
        this.trades = trades;
        this.idempotency = idempotency;
        this.invalidator = invalidator;
        this.marketService = marketService;
        this.seasonService = seasonService;
        this.seasonPlayers = seasonPlayers;
        this.relationships = relationships;
        this.analytics = analytics;
        this.props = props;
        this.tx = tx;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.eventBus = eventBus;
        this.meterRegistry = meterRegistry;
    }

    public AcceptResult accept(AcceptCommand cmd) {
        Negotiation negotiation = negotiations.findById(cmd.negotiationId())
                .orElseThrow(() -> ApiException.notFound("NEGOTIATION_NOT_FOUND", "협상이 없습니다."));
        String scopedKey = negotiation.getPlayerId() + ":" + cmd.idempotencyKey();

        var replayed = idempotency.findById(scopedKey);
        if (replayed.isPresent()) {
            return new AcceptResult(replayed.get().getHttpStatus(), readBody(replayed.get().getResponseBody()));
        }

        AcceptResult result;
        try {
            result = tx.execute(status -> doCommit(cmd, negotiation.getPlayerId()));
        } catch (CommitConflict conflict) {
            // 거래 롤백 후에도 무효화 기록은 별도 트랜잭션으로 남긴다.
            tx.executeWithoutResult(status -> invalidateAfterConflict(cmd.negotiationId(), conflict.reason));
            result = new AcceptResult(409, Map.of("code", conflict.code, "message", conflict.message));
        }

        AcceptResult finalResult = result;
        try {
            tx.executeWithoutResult(status -> idempotency.save(new IdempotencyRecord(
                    scopedKey, negotiation.getPlayerId(), finalResult.httpStatus(), writeBody(finalResult.body()),
                    Instant.now().plus(props.idempotency().retention()))));
        } catch (DataIntegrityViolationException raced) {
            // 동일한 멱등 키가 경합하면 먼저 저장된 결과를 반환한다.
            var stored = idempotency.findById(scopedKey).orElseThrow();
            return new AcceptResult(stored.getHttpStatus(), readBody(stored.getResponseBody()));
        }
        return result;
    }

    private AcceptResult doCommit(AcceptCommand cmd, Long playerId) {
        Negotiation negotiation = negotiations.findById(cmd.negotiationId()).orElseThrow();
        if (!negotiation.isActive()) {
            throw new CommitConflict(null, "NEGOTIATION_CLOSED", NegotiationService.closedMessage(negotiation));
        }

        Offer offer = offers.findById(cmd.offerId())
                .filter(o -> o.getNegotiationId().equals(cmd.negotiationId()))
                .orElseThrow(() -> ApiException.notFound("OFFER_NOT_FOUND", "제안이 없습니다."));
        if (!offer.isStanding()) {
            throw new CommitConflict(null, "OFFER_NOT_STANDING", "이미 처리된 제안입니다.");
        }

        Offer.Proposer expectedProposer =
                cmd.acceptor() == Acceptor.PLAYER ? Offer.Proposer.PERSONA : Offer.Proposer.PLAYER;
        if (offer.getProposer() != expectedProposer) {
            throw ApiException.conflict("WRONG_SIDE", "상대의 제안만 수락할 수 있습니다.");
        }

        if (listings.consumeIfVersionMatches(negotiation.getListingId(), cmd.expectedListingVersion()) == 0) {
            throw new CommitConflict(Negotiation.InvalidationReason.HIJACKED,
                    "LISTING_CONFLICT", "이미 거래된 매물입니다. 다른 사람이 먼저 성사시켰습니다.");
        }
        Holding given = holdings.findById(offer.getPlayerHoldingId()).orElseThrow();
        if (holdings.releaseIfVersionMatches(given.getId(), cmd.expectedHoldingVersion()) == 0) {
            throw new CommitConflict(Negotiation.InvalidationReason.VERSION_CONFLICT,
                    "HOLDING_CONFLICT", "내주기로 한 물건이 이미 다른 거래에 나갔습니다.");
        }

        Listing listing = listings.findById(negotiation.getListingId()).orElseThrow();
        Holding received = holdings.save(new Holding(playerId, listing.getItemId()));

        Item givenItem = items.findById(given.getItemId()).orElseThrow();
        Item receivedItem = items.findById(listing.getItemId()).orElseThrow();
        BigDecimal personaValueRatio = personaValueRatio(givenItem, receivedItem);

        offer.resolve(Offer.Status.ACCEPTED);
        offers.findByNegotiationIdAndStatus(cmd.negotiationId(), Offer.Status.STANDING)
                .forEach(other -> other.resolve(Offer.Status.INVALIDATED));
        negotiation.complete();
        Trade trade = trades.save(new Trade(cmd.negotiationId(), playerId, listing.getId(),
                given.getId(), received.getId(), offer.getProposer().name(), personaValueRatio));
        observeSettlement(cmd.negotiationId(), trade, offer.getProposer(), personaValueRatio);

        invalidator.invalidateActiveOnListing(listing.getId(), cmd.negotiationId(),
                Negotiation.InvalidationReason.HIJACKED);

        marketService.maybeRecycle(given.getItemId());

        boolean justCleared = settleSeason(playerId, trade);
        updateRelationship(playerId, listing.getPersonaId());
        recordTradeAnalytics(playerId, trade, givenItem, receivedItem, cmd.acceptor());

        eventBus.publish(cmd.negotiationId());

        Map<String, Object> body = new HashMap<>();
        body.put("tradeId", trade.getId());
        body.put("negotiationId", cmd.negotiationId());
        body.put("givenItemName", givenItem.getName());
        body.put("receivedItemName", receivedItem.getName());
        body.put("cleared", justCleared);
        return new AcceptResult(200, body);
    }

    private boolean settleSeason(Long playerId, Trade trade) {
        Long seasonId = seasonService.current().getId();
        SeasonPlayer participant = seasonService.ensureParticipant(seasonId, playerId);
        BigDecimal inventoryValue = holdings.sumActiveInventoryValue(playerId);
        participant.recordTrade(inventoryValue);

        if (participant.isCleared() || inventoryValue.compareTo(props.value().clearThreshold()) < 0) {
            return false;
        }
        // 동일 시즌의 클리어 순번이 겹치지 않도록 채번을 직렬화한다.
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, seasonId);
        participant.markCleared(seasonPlayers.countCleared(seasonId) + 1);
        analytics.record(AnalyticsRecorder.CLEAR, playerId,
                Map.of("seasonId", seasonId, "clearRank", participant.getClearRank(), "tradeId", trade.getId()));
        log.info("클리어 도달: playerId={}, rank={}", playerId, participant.getClearRank());
        return true;
    }

    private BigDecimal personaValueRatio(Item personaReceives, Item personaGives) {
        BigDecimal gives = personaGives.effectiveValue();
        if (gives.signum() == 0) {
            return null;
        }
        return personaReceives.effectiveValue().divide(gives, 4, RoundingMode.HALF_UP);
    }

    private void observeSettlement(Long negotiationId, Trade trade, Offer.Proposer proposer, BigDecimal ratio) {
        boolean personaLoss = ratio != null && ratio.compareTo(props.value().personaLossRatio()) < 0;
        if (ratio != null) {
            meterRegistry.summary("trade.persona.value.ratio").record(ratio.doubleValue());
        }
        meterRegistry.counter("trade.completed", "proposer", proposer.name(),
                "persona_loss", Boolean.toString(personaLoss)).increment();
        if (personaLoss && proposer == Offer.Proposer.PERSONA) {
            log.warn("성사 관측: 페르소나 발의 손해 거래 — tradeId={}, negotiationId={}, ratio={} (조작 의심)",
                    trade.getId(), negotiationId, ratio);
        }
    }

    private void updateRelationship(Long playerId, Long personaId) {
        Relationship relationship = relationships.findById(new Relationship.Key(playerId, personaId))
                .orElseGet(() -> relationships.save(new Relationship(playerId, personaId)));
        relationship.recordTrade();
    }

    private void recordTradeAnalytics(Long playerId, Trade trade, Item given, Item received, Acceptor acceptor) {

        BigDecimal delta = received.effectiveValue().subtract(given.effectiveValue());
        Map<String, Object> payload = new HashMap<>();
        payload.put("tradeId", trade.getId());
        payload.put("negotiationId", trade.getNegotiationId());
        payload.put("listingId", trade.getListingId());
        payload.put("givenItemId", given.getId());
        payload.put("receivedItemId", received.getId());
        payload.put("valueDelta", delta);
        payload.put("acceptedBy", acceptor.name());
        analytics.record(AnalyticsRecorder.TRADE, playerId, payload);
        analytics.record(AnalyticsRecorder.ACCEPT, playerId,
                Map.of("negotiationId", trade.getNegotiationId(), "acceptedBy", acceptor.name()));
    }

    private void invalidateAfterConflict(Long negotiationId, Negotiation.InvalidationReason reason) {
        if (reason == null) {
            return;
        }
        Negotiation negotiation = negotiations.findById(negotiationId).orElseThrow();
        if (!negotiation.isActive()) {
            return;
        }
        negotiation.invalidate(reason);
        offers.findByNegotiationIdAndStatus(negotiationId, Offer.Status.STANDING)
                .forEach(offer -> offer.resolve(Offer.Status.INVALIDATED));
        if (reason == Negotiation.InvalidationReason.HIJACKED) {
            analytics.record(AnalyticsRecorder.HIJACK, negotiation.getPlayerId(),
                    Map.of("negotiationId", negotiationId, "listingId", negotiation.getListingId()));
        }
        eventBus.publish(negotiationId);
    }

    private Map<String, Object> readBody(String json) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return map;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("멱등 결과 역직렬화 실패", e);
        }
    }

    private String writeBody(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("멱등 결과 직렬화 실패", e);
        }
    }
}
