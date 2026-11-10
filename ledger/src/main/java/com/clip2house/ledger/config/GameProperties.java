package com.clip2house.ledger.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clip2house")
public record GameProperties(
        Market market,
        Value value,
        Auth auth,
        Idempotency idempotency,
        Season season,
        Negotiation negotiation,
        Retention retention,
        RateLimit rateLimit,
        Orchestrator orchestrator,
        Internal internal,
        Report report,
        PersonaStats personaStats
) {

    public record Market(
            int targetActiveListings,
            Duration listingTtl,
            double recycleProbability,
            double supplyLogMin,
            double supplyLogMax,
            double dictionaryMatchTolerance,
            Duration faucetInterval,
            int faucetBatchCap,
            Duration expiryInterval
    ) {}

    public record Value(double grossLogDistance, BigDecimal clearThreshold, BigDecimal personaLossRatio) {}

    public record Auth(Duration guestTokenTtl, Duration accountTokenTtl) {}

    public record Idempotency(Duration retention, Duration cleanupInterval) {}

    public record Season(Duration length) {}

    public record Negotiation(Duration recoveryInterval, Duration recoveryMinAge) {}

    public record Retention(int auditMonths, int analyticsMonths, Duration partitionMaintenanceInterval,
                            String archiveDir) {}

    public record RateLimit(
            int guestPerIpPerHour,
            int messagesPerPlayerPerMinute,
            int requestsPerIpPerMinute,
            int frictionThreshold,
            Duration frictionWindow,
            int frictionMessagesPerMinute
    ) {}

    public record Orchestrator(String baseUrl, Duration connectTimeout, Duration readTimeout) {}

    public record Internal(String apiSecret) {}

    public record Report(String weeklyCron, String webhookUrl) {}

    public record PersonaStats(Duration refreshInterval, Duration lookback) {}
}
