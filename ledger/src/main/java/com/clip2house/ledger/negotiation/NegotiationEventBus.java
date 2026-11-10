package com.clip2house.ledger.negotiation;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class NegotiationEventBus {

    private static final Logger log = LoggerFactory.getLogger(NegotiationEventBus.class);
    private static final long EMITTER_TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long negotiationId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        List<SseEmitter> emitters = subscribers.computeIfAbsent(negotiationId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            // 첫 변경 이벤트 전에 연결을 알리도록 응답 헤더를 즉시 전송한다.

            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException e) {
            remove.run();
        }
        return emitter;
    }

    public void publish(Long negotiationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(negotiationId);
                }
            });
        } else {
            send(negotiationId);
        }
    }

    private void send(Long negotiationId) {
        List<SseEmitter> emitters = subscribers.get(negotiationId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("update").data("changed"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("SSE 수신자 제거: negotiation={}", negotiationId);
            }
        }
    }
}
