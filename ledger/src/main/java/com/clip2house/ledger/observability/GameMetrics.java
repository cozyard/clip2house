package com.clip2house.ledger.observability;

import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.market.ListingRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class GameMetrics {

    public GameMetrics(MeterRegistry registry, ListingRepository listings) {
        Gauge.builder("market.listings.active", () -> listings.countByStatus(Listing.Status.ACTIVE))
                .description("현재 Active 매물 수 (Faucet 목표 대비)")
                .register(registry);
    }
}
