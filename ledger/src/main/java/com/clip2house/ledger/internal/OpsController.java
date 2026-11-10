package com.clip2house.ledger.internal;

import com.clip2house.ledger.catalog.ItemDictionaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ops")
public class OpsController {

    private final ItemDictionaryService dictionary;

    public OpsController(ItemDictionaryService dictionary) {
        this.dictionary = dictionary;
    }

    public record InvalidateCohortRequest(@NotBlank String genVersion) {}

    @PostMapping("/cohorts/invalidate")
    public Map<String, Object> invalidateCohort(@Valid @RequestBody InvalidateCohortRequest request) {
        int count = dictionary.invalidateCohort(request.genVersion());
        return Map.of("invalidated", count);
    }

    public record CorrectItemRequest(@NotNull @Positive BigDecimal correctedValue) {}

    @PostMapping("/items/{itemId}/correct")
    public Map<String, Object> correctItem(@PathVariable Long itemId,
                                           @Valid @RequestBody CorrectItemRequest request) {
        dictionary.correct(itemId, request.correctedValue());
        return Map.of("corrected", true);
    }
}
