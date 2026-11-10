package com.clip2house.ledger.internal;

import com.clip2house.ledger.config.GameProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrchestratorClient {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorClient.class);

    private final RestClient restClient;

    public OrchestratorClient(GameProperties props) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) props.orchestrator().connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) props.orchestrator().readTimeout().toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(props.orchestrator().baseUrl())
                .defaultHeader("X-Internal-Secret", props.internal().apiSecret())
                .requestFactory(requestFactory)
                .build();
    }

    public record AnchorPayload(String name, BigDecimal value) {}

    public record GeneratedItem(String name, String description, BigDecimal proposedValue, String citedAnchorName,
                                String genVersion, String genModel, String genPromptId) {}

    public Optional<GeneratedItem> generateItem(double targetLog10Value, List<AnchorPayload> anchors,
                                                List<String> existingNames) {
        try {
            GeneratedItem generated = restClient.post()
                    .uri("/internal/items/generate")
                    .body(Map.of(
                            "targetLog10Value", targetLog10Value,
                            "anchors", anchors,
                            "existingNames", existingNames))
                    .retrieve()
                    .body(GeneratedItem.class);
            return Optional.ofNullable(generated);
        } catch (Exception e) {
            log.warn("아이템 생성 요청 실패, 사전으로 대체: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Async
    public void notifyTurn(Long negotiationId, Long personaId) {
        try {
            restClient.post()
                    .uri("/internal/turns")
                    .body(Map.of("negotiationId", negotiationId, "personaId", personaId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.debug("턴 알림 실패(페르소나 무응답으로 흡수): negotiationId={}, {}", negotiationId, e.getMessage());
        }
    }
}
