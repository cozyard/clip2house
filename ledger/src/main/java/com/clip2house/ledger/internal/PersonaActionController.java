package com.clip2house.ledger.internal;

import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.negotiation.TradeCommitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/negotiations")
public class PersonaActionController {

    public enum Action { CONTINUE, OFFER, ACCEPT, DECLINE }

    private final PersonaActionService actionService;
    private final TradeCommitService tradeCommitService;

    public PersonaActionController(PersonaActionService actionService, TradeCommitService tradeCommitService) {
        this.actionService = actionService;
        this.tradeCommitService = tradeCommitService;
    }

    public record ActionRequest(
            @NotNull Action action,
            @NotNull @Size(min = 1, max = 3) List<@NotBlank @Size(max = 2000) String> messages,
            @NotBlank String turnId,
            Long offerHoldingId,
            Long acceptOfferId,
            Long expectedListingVersion,
            Long expectedHoldingVersion) {}

    @PostMapping("/{negotiationId}/actions")
    public ResponseEntity<Map<String, Object>> act(@PathVariable Long negotiationId,
                                                   @Valid @RequestBody ActionRequest request) {
        return switch (request.action()) {
            case CONTINUE -> ResponseEntity.ok(actionService.applyContinue(negotiationId, request.messages()));
            case OFFER -> ResponseEntity.ok(
                    actionService.applyOffer(negotiationId, request.offerHoldingId(), request.messages()));
            case DECLINE -> ResponseEntity.ok(actionService.applyDecline(negotiationId, request.messages()));
            case ACCEPT -> acceptAndSpeak(negotiationId, request);
        };
    }

    private ResponseEntity<Map<String, Object>> acceptAndSpeak(Long negotiationId, ActionRequest request) {
        if (request.acceptOfferId() == null
            || request.expectedListingVersion() == null || request.expectedHoldingVersion() == null) {
            throw ApiException.badRequest("MISSING_ACCEPT_FIELDS",
                    "ACCEPT에는 acceptOfferId와 기대 Version들이 필요합니다.");
        }
        var result = tradeCommitService.accept(new TradeCommitService.AcceptCommand(
                negotiationId, request.acceptOfferId(), request.expectedListingVersion(),
                request.expectedHoldingVersion(), request.turnId(), TradeCommitService.Acceptor.PERSONA));
        if (result.success()) {
            actionService.saveMessages(negotiationId, request.messages());
        }
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }
}
