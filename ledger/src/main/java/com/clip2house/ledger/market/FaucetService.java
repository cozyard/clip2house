package com.clip2house.ledger.market;

import com.clip2house.ledger.catalog.Item;
import com.clip2house.ledger.catalog.ItemDictionaryService;
import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.catalog.GoldenAnchorRepository;
import com.clip2house.ledger.config.GameProperties;
import com.clip2house.ledger.internal.OrchestratorClient;
import com.clip2house.ledger.persona.PersonaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaucetService {

    private static final Logger log = LoggerFactory.getLogger(FaucetService.class);

    private final ListingRepository listings;
    private final ItemRepository items;
    private final GoldenAnchorRepository anchors;
    private final ItemDictionaryService dictionary;
    private final PersonaRepository personas;
    private final OrchestratorClient orchestrator;
    private final GameProperties props;
    private final RandomGenerator random;
    private final Counter dictionaryHits;
    private final Counter dictionaryMisses;

    public FaucetService(ListingRepository listings, ItemRepository items, GoldenAnchorRepository anchors,
                         ItemDictionaryService dictionary, PersonaRepository personas,
                         OrchestratorClient orchestrator, GameProperties props, RandomGenerator random,
                         MeterRegistry meterRegistry) {
        this.listings = listings;
        this.items = items;
        this.anchors = anchors;
        this.dictionary = dictionary;
        this.personas = personas;
        this.orchestrator = orchestrator;
        this.props = props;
        this.random = random;

        this.dictionaryHits = meterRegistry.counter("faucet.dictionary", "result", "hit");
        this.dictionaryMisses = meterRegistry.counter("faucet.dictionary", "result", "miss");
    }

    @Scheduled(fixedDelayString = "${clip2house.market.faucet-interval}")
    public void refillScheduled() {
        refill();
    }

    @Transactional
    public int refill() {
        long active = listings.countByStatus(Listing.Status.ACTIVE);
        long deficit = props.market().targetActiveListings() - active;
        if (deficit <= 0) {
            return 0;
        }
        if (personas.findByActiveTrue().isEmpty()) {
            log.warn("활성 페르소나가 없어 Faucet을 건너뜁니다. 오케스트레이터 동기화를 기다립니다.");
            return 0;
        }

        int toCreate = (int) Math.min(deficit, props.market().faucetBatchCap());
        int created = 0;
        for (int i = 0; i < toCreate; i++) {
            double targetLog = props.market().supplyLogMin()
                    + random.nextDouble() * (props.market().supplyLogMax() - props.market().supplyLogMin());
            Item item = resolveItem(targetLog);
            if (item == null) {
                continue;
            }
            listings.save(new Listing(item.getId(), personas.pickRandomActive().getId(),
                    Listing.Origin.FAUCET, Instant.now().plus(props.market().listingTtl())));
            created++;
        }
        log.info("Faucet 발화: active={}, 신규 매물 {}건", active, created);
        return created;
    }

    private Item resolveItem(double targetLog) {
        Item nearest = items.findNearestValid(targetLog).orElse(null);
        boolean nearEnough = nearest != null
                && Math.abs(Math.log10(nearest.effectiveValue().doubleValue()) - targetLog)
                   <= props.market().dictionaryMatchTolerance();
        if (nearEnough) {
            dictionaryHits.increment();
            return nearest;
        }

        dictionaryMisses.increment();
        var anchorPayloads = anchors.findAll().stream()
                .map(a -> new OrchestratorClient.AnchorPayload(a.getName(), a.getRefValue()))
                .toList();

        List<String> existingNames = items.findTop200ByOrderByIdDesc().stream().map(Item::getName).toList();
        return orchestrator.generateItem(targetLog, anchorPayloads, existingNames)
                .map(g -> {
                    try {
                        return dictionary.registerProposal(new ItemDictionaryService.Proposal(
                                g.name(), g.description(), g.proposedValue(), g.citedAnchorName(),
                                g.genVersion(), g.genModel(), g.genPromptId()));
                    } catch (Exception e) {
                        log.warn("생성 아이템 검증 실패, 사전으로 대체: {}", e.getMessage());
                        return nearest;
                    }
                })
                .orElse(nearest);
    }
}
