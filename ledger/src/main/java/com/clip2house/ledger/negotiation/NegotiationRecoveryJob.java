package com.clip2house.ledger.negotiation;

import java.time.Instant;
import java.util.List;
import com.clip2house.ledger.config.GameProperties;
import com.clip2house.ledger.internal.OrchestratorClient;
import com.clip2house.ledger.market.ListingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NegotiationRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(NegotiationRecoveryJob.class);

    private final NegotiationRepository negotiations;
    private final ListingRepository listings;
    private final OrchestratorClient orchestrator;
    private final GameProperties props;

    public NegotiationRecoveryJob(NegotiationRepository negotiations, ListingRepository listings,
                                  OrchestratorClient orchestrator, GameProperties props) {
        this.negotiations = negotiations;
        this.listings = listings;
        this.orchestrator = orchestrator;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${clip2house.negotiation.recovery-interval}")
    public void resweep() {
        Instant cutoff = Instant.now().minus(props.negotiation().recoveryMinAge());
        List<Negotiation> stranded =
                negotiations.findStrandedAwaitingPersona(Negotiation.Status.ACTIVE, cutoff);
        for (Negotiation negotiation : stranded) {
            listings.findById(negotiation.getListingId()).ifPresent(listing ->
                    orchestrator.notifyTurn(negotiation.getId(), listing.getPersonaId()));
        }
        if (!stranded.isEmpty()) {
            log.info("무응답 복구 스윕: 방치된 협상 {}건 재트리거", stranded.size());
        }
    }
}
