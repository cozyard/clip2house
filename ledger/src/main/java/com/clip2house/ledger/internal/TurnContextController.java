package com.clip2house.ledger.internal;

import com.clip2house.ledger.auth.PlayerRepository;
import com.clip2house.ledger.catalog.Item;
import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.market.ListingRepository;
import com.clip2house.ledger.negotiation.ChatMessage;
import com.clip2house.ledger.negotiation.ChatMessageRepository;
import com.clip2house.ledger.negotiation.Negotiation;
import com.clip2house.ledger.negotiation.NegotiationRepository;
import com.clip2house.ledger.negotiation.Offer;
import com.clip2house.ledger.negotiation.OfferRepository;
import com.clip2house.ledger.persona.Relationship;
import com.clip2house.ledger.persona.RelationshipRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/negotiations")
public class TurnContextController {

    private final NegotiationRepository negotiations;
    private final ListingRepository listings;
    private final ItemRepository items;
    private final HoldingRepository holdings;
    private final ChatMessageRepository messages;
    private final OfferRepository offers;
    private final RelationshipRepository relationships;
    private final PlayerRepository players;

    public TurnContextController(NegotiationRepository negotiations, ListingRepository listings,
                                 ItemRepository items, HoldingRepository holdings, ChatMessageRepository messages,
                                 OfferRepository offers, RelationshipRepository relationships,
                                 PlayerRepository players) {
        this.negotiations = negotiations;
        this.listings = listings;
        this.items = items;
        this.holdings = holdings;
        this.messages = messages;
        this.offers = offers;
        this.relationships = relationships;
        this.players = players;
    }

    public record ListingView(Long listingId, long listingVersion, String itemName, String description) {}

    public record HoldingCandidate(Long holdingId, long holdingVersion, String itemName, String description,
                                   BigDecimal valueRatio) {}

    public record OfferView(Long offerId, String proposer, Long playerHoldingId, long playerHoldingVersion,
                            String playerItemName, BigDecimal valueRatio) {}

    public record RelationshipView(String lastOutcome, boolean injectionAttempted, String standing, int tradesCount) {}

    public record MessageView(Long id, String content, Instant createdAt) {}

    public record TurnContext(Long negotiationId, String status, Long personaId, Long playerId,
                              String playerNickname, ListingView listing, RelationshipView relationship,
                              List<MessageView> unreadPlayerMessages, List<OfferView> standingOffers,
                              List<HoldingCandidate> playerHoldings) {}

    @GetMapping("/{negotiationId}/context")
    @Transactional
    public TurnContext context(@PathVariable Long negotiationId) {
        Negotiation negotiation = negotiations.findById(negotiationId)
                .orElseThrow(() -> ApiException.notFound("NEGOTIATION_NOT_FOUND", "협상이 없습니다."));
        Listing listing = listings.findById(negotiation.getListingId()).orElseThrow();
        Item listingItem = items.findById(listing.getItemId()).orElseThrow();

        List<MessageView> unread = messages.findUnansweredPlayerMessages(negotiationId).stream()
                .map(m -> new MessageView(m.getId(), m.getContent(), m.getCreatedAt()))
                .toList();

        messages.markPlayerMessagesRead(negotiationId, Instant.now());

        List<OfferView> standingOffers = offers.findByNegotiationIdAndStatus(negotiationId, Offer.Status.STANDING)
                .stream()
                .map(o -> {
                    Holding holding = holdings.findById(o.getPlayerHoldingId()).orElseThrow();
                    Item holdingItem = items.findById(holding.getItemId()).orElseThrow();
                    return new OfferView(o.getId(), o.getProposer().name(), holding.getId(), holding.getVersion(),
                            holdingItem.getName(), ratio(holdingItem, listingItem));
                })
                .toList();

        List<HoldingCandidate> candidates = holdings
                .findByPlayerIdAndStatus(negotiation.getPlayerId(), Holding.Status.ACTIVE).stream()
                .map(h -> {
                    Item item = items.findById(h.getItemId()).orElseThrow();
                    return new HoldingCandidate(h.getId(), h.getVersion(), item.getName(), item.getDescription(),
                            ratio(item, listingItem));
                })
                .toList();

        RelationshipView relationshipView = relationships
                .findById(new Relationship.Key(negotiation.getPlayerId(), listing.getPersonaId()))
                .map(r -> new RelationshipView(r.getLastOutcome().name(), r.isInjectionAttempted(),
                        r.getStanding().name(), r.getTradesCount()))
                .orElse(new RelationshipView("NONE", false, "NEUTRAL", 0));

        String playerNickname = players.findById(negotiation.getPlayerId()).orElseThrow().getNickname();
        return new TurnContext(negotiationId, negotiation.getStatus().name(), listing.getPersonaId(),
                negotiation.getPlayerId(), playerNickname,
                new ListingView(listing.getId(), listing.getVersion(), listingItem.getName(),
                        listingItem.getDescription()), relationshipView, unread, standingOffers, candidates);
    }

    private static BigDecimal ratio(Item playerItem, Item listingItem) {
        return playerItem.effectiveValue().divide(listingItem.effectiveValue(), 4, RoundingMode.HALF_UP);
    }
}
