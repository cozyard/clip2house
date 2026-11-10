package com.clip2house.ledger.negotiation;

import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.market.ListingRepository;
import com.clip2house.ledger.persona.PersonaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/negotiations")
public class NegotiationController {

    private final NegotiationService negotiationService;
    private final TradeCommitService tradeCommitService;
    private final NegotiationEventBus eventBus;
    private final NegotiationRepository negotiations;
    private final ChatMessageRepository messages;
    private final OfferRepository offers;
    private final ListingRepository listings;
    private final HoldingRepository holdings;
    private final ItemRepository items;
    private final PersonaRepository personas;

    public NegotiationController(NegotiationService negotiationService, TradeCommitService tradeCommitService,
                                 NegotiationEventBus eventBus, NegotiationRepository negotiations,
                                 ChatMessageRepository messages, OfferRepository offers,
                                 ListingRepository listings, HoldingRepository holdings,
                                 ItemRepository items, PersonaRepository personas) {
        this.negotiationService = negotiationService;
        this.tradeCommitService = tradeCommitService;
        this.eventBus = eventBus;
        this.negotiations = negotiations;
        this.messages = messages;
        this.offers = offers;
        this.listings = listings;
        this.holdings = holdings;
        this.items = items;
        this.personas = personas;
    }

    public record OpenRequest(@NotNull Long listingId) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> open(@RequestAttribute("playerId") Long playerId,
                                    @Valid @RequestBody OpenRequest request) {
        Negotiation negotiation = negotiationService.open(playerId, request.listingId());
        return Map.of("negotiationId", negotiation.getId(), "listingId", negotiation.getListingId());
    }

    public record ChatSummary(Long negotiationId, Long listingId, String itemName, String personaNickname,
                              String lastSender, String lastMessage, Instant lastAt, boolean awaitingPersona) {}

    @GetMapping
    public List<ChatSummary> myChats(@RequestAttribute("playerId") Long playerId) {
        return negotiations.findByPlayerIdAndStatusOrderByCreatedAtDesc(playerId, Negotiation.Status.ACTIVE).stream()
                .map(negotiation -> {
                    List<ChatMessage> msgs = messages.findByNegotiationIdOrderByIdAsc(negotiation.getId());
                    if (msgs.isEmpty()) {
                        return null;
                    }
                    ChatMessage last = msgs.get(msgs.size() - 1);
                    var listing = listings.findById(negotiation.getListingId()).orElseThrow();
                    var item = items.findById(listing.getItemId()).orElseThrow();
                    var persona = personas.findById(listing.getPersonaId()).orElseThrow();
                    boolean awaiting = "PLAYER".equals(last.getSender().name());
                    return new ChatSummary(negotiation.getId(), listing.getId(), item.getName(),
                            persona.getNickname(), last.getSender().name(), last.getContent(),
                            last.getCreatedAt(), awaiting);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(ChatSummary::lastAt).reversed())
                .toList();
    }

    public record MessageView(Long id, String sender, String content, Instant createdAt, Instant readAt) {}

    public record OfferView(Long offerId, String proposer, Long playerHoldingId, long playerHoldingVersion,
                            String playerItemName, String status, Instant createdAt) {}

    public record NegotiationView(Long negotiationId, String status, String statusDetail,
                                  Long listingId, long listingVersion, String listingItemName,
                                  String sellerNickname, Integer sellerAvgResponseSeconds,
                                  List<MessageView> messages, List<OfferView> offers) {}

    @GetMapping("/{negotiationId}")
    public NegotiationView view(@RequestAttribute("playerId") Long playerId, @PathVariable Long negotiationId) {
        Negotiation negotiation = owned(playerId, negotiationId);
        var listing = listings.findById(negotiation.getListingId()).orElseThrow();
        var listingItem = items.findById(listing.getItemId()).orElseThrow();
        var persona = personas.findById(listing.getPersonaId()).orElseThrow();

        List<MessageView> messageViews = messages.findByNegotiationIdOrderByIdAsc(negotiationId).stream()
                .map(m -> new MessageView(m.getId(), m.getSender().name(), m.getContent(), m.getCreatedAt(), m.getReadAt()))
                .toList();
        List<OfferView> offerViews = offers.findByNegotiationIdAndStatus(negotiationId, Offer.Status.STANDING).stream()
                .map(o -> {
                    var holding = holdings.findById(o.getPlayerHoldingId()).orElseThrow();
                    return new OfferView(o.getId(), o.getProposer().name(), holding.getId(), holding.getVersion(),
                            holdingItemName(holding.getId()), o.getStatus().name(), o.getCreatedAt());
                })
                .toList();
        String statusDetail = negotiation.isActive() ? null : NegotiationService.closedMessage(negotiation);
        return new NegotiationView(negotiation.getId(), negotiation.getStatus().name(), statusDetail,
                listing.getId(), listing.getVersion(), listingItem.getName(), persona.getNickname(),
                persona.getAvgResponseSeconds(), messageViews, offerViews);
    }

    public record PostMessageRequest(@NotBlank @Size(max = 2000) String content) {}

    @PostMapping("/{negotiationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> postMessage(@RequestAttribute("playerId") Long playerId,
                                           @PathVariable Long negotiationId,
                                           @Valid @RequestBody PostMessageRequest request) {
        ChatMessage saved = negotiationService.postMessage(playerId, negotiationId, request.content());
        return Map.of("messageId", saved.getId());
    }

    public record PlaceOfferRequest(@NotNull Long holdingId) {}

    @PostMapping("/{negotiationId}/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> placeOffer(@RequestAttribute("playerId") Long playerId,
                                          @PathVariable Long negotiationId,
                                          @Valid @RequestBody PlaceOfferRequest request) {
        Offer offer = negotiationService.placeOffer(playerId, negotiationId, request.holdingId());
        return Map.of("offerId", offer.getId());
    }

    public record AcceptRequest(@NotNull Long offerId, @NotNull Long expectedListingVersion,
                                @NotNull Long expectedHoldingVersion) {}

    @PostMapping("/{negotiationId}/accept")
    public ResponseEntity<Map<String, Object>> accept(@RequestAttribute("playerId") Long playerId,
                                                      @PathVariable Long negotiationId,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      @Valid @RequestBody AcceptRequest request) {
        owned(playerId, negotiationId);
        var result = tradeCommitService.accept(new TradeCommitService.AcceptCommand(
                negotiationId, request.offerId(), request.expectedListingVersion(),
                request.expectedHoldingVersion(), idempotencyKey, TradeCommitService.Acceptor.PLAYER));
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }

    @PostMapping("/{negotiationId}/offers/{offerId}/decline")
    public Map<String, Object> decline(@RequestAttribute("playerId") Long playerId,
                                       @PathVariable Long negotiationId, @PathVariable Long offerId) {
        negotiationService.declineOffer(playerId, negotiationId, offerId);
        return Map.of("declined", true);
    }

    @GetMapping("/{negotiationId}/events")
    public SseEmitter events(@RequestAttribute("playerId") Long playerId, @PathVariable Long negotiationId) {
        owned(playerId, negotiationId);
        return eventBus.subscribe(negotiationId);
    }

    private Negotiation owned(Long playerId, Long negotiationId) {
        return negotiations.findById(negotiationId)
                .filter(n -> n.getPlayerId().equals(playerId))
                .orElseThrow(() -> ApiException.notFound("NEGOTIATION_NOT_FOUND", "협상이 없습니다."));
    }

    private String holdingItemName(Long holdingId) {
        return holdings.findById(holdingId)
                .flatMap(h -> items.findById(h.getItemId()))
                .map(i -> i.getName())
                .orElse(null);
    }
}
