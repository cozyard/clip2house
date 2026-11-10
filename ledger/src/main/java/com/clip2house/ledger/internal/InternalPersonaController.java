package com.clip2house.ledger.internal;

import com.clip2house.ledger.persona.Persona;
import com.clip2house.ledger.persona.PersonaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/personas")
public class InternalPersonaController {

    private final PersonaRepository personas;

    public InternalPersonaController(PersonaRepository personas) {
        this.personas = personas;
    }

    public record PersonaPayload(@NotNull Long id, @NotNull String nickname, String community) {}

    public record SyncRequest(@NotEmpty List<@Valid PersonaPayload> personas) {}

    @PutMapping
    @Transactional
    public Map<String, Object> sync(@Valid @RequestBody SyncRequest request) {
        Set<Long> incoming = request.personas().stream().map(PersonaPayload::id).collect(Collectors.toSet());
        personas.findAll().forEach(existing -> {
            if (!incoming.contains(existing.getId())) {
                existing.update(existing.getNickname(), existing.getCommunity(), false);
            }
        });
        for (PersonaPayload payload : request.personas()) {
            personas.findById(payload.id())
                    .ifPresentOrElse(
                            existing -> existing.update(payload.nickname(), payload.community(), true),
                            () -> personas.save(new Persona(payload.id(), payload.nickname(), payload.community())));
        }
        return Map.of("synced", request.personas().size());
    }
}
