package com.clip2house.ledger.internal;

import com.clip2house.ledger.auth.RateLimiter;
import com.clip2house.ledger.observability.AnalyticsRecorder;
import com.clip2house.ledger.observability.AuditRecorder;
import com.clip2house.ledger.persona.Relationship;
import com.clip2house.ledger.persona.RelationshipRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit")
public class InternalAuditController {

    private final AuditRecorder audit;
    private final AnalyticsRecorder analytics;
    private final RelationshipRepository relationships;
    private final RateLimiter rateLimiter;

    public InternalAuditController(AuditRecorder audit, AnalyticsRecorder analytics,
                                   RelationshipRepository relationships, RateLimiter rateLimiter) {
        this.audit = audit;
        this.analytics = analytics;
        this.relationships = relationships;
        this.rateLimiter = rateLimiter;
    }

    public record InjectionReport(@NotNull Long playerId, Long personaId, Long negotiationId,
                                  @NotBlank String rawInput, @NotBlank String classifiedType, String botResponse) {}

    @PostMapping("/injection")
    @Transactional
    public Map<String, Object> reportInjection(@Valid @RequestBody InjectionReport report) {
        audit.recordInjection(report.playerId(), report.personaId(), report.negotiationId(),
                report.rawInput(), report.classifiedType(), report.botResponse());

        Map<String, Object> payload = new HashMap<>();
        payload.put("classifiedType", report.classifiedType());
        payload.put("negotiationId", report.negotiationId());
        analytics.record(AnalyticsRecorder.CLASSIFIER_HIT, report.playerId(), payload);

        if (report.personaId() != null) {
            Relationship relationship = relationships
                    .findById(new Relationship.Key(report.playerId(), report.personaId()))
                    .orElseGet(() -> relationships.save(new Relationship(report.playerId(), report.personaId())));
            relationship.recordInjectionAttempt();
        }

        rateLimiter.recordClassifierHit(report.playerId());
        return Map.of("recorded", true);
    }

    public record OfflineDegradeReport(@NotNull Long personaId, String reason, Long negotiationId) {}

    @PostMapping("/offline-degrade")
    public Map<String, Object> reportOfflineDegrade(@Valid @RequestBody OfflineDegradeReport report) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("personaId", report.personaId());
        payload.put("reason", report.reason());
        payload.put("negotiationId", report.negotiationId());
        analytics.record(AnalyticsRecorder.OFFLINE_DEGRADE, null, payload);
        return Map.of("recorded", true);
    }
}
