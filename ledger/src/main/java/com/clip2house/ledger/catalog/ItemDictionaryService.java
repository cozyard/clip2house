package com.clip2house.ledger.catalog;

import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.config.GameProperties;
import java.math.BigDecimal;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemDictionaryService {

    private static final Logger log = LoggerFactory.getLogger(ItemDictionaryService.class);

    private final ItemRepository items;
    private final GoldenAnchorRepository anchors;
    private final GameProperties props;

    public ItemDictionaryService(ItemRepository items, GoldenAnchorRepository anchors, GameProperties props) {
        this.items = items;
        this.anchors = anchors;
        this.props = props;
    }

    public record Proposal(String name, String description, BigDecimal proposedValue, String citedAnchorName,
                           String genVersion, String genModel, String genPromptId) {}

    @Transactional
    public Item registerProposal(Proposal proposal) {
        String nameKey = normalizeKey(proposal.name());
        var existing = items.findByNameKey(nameKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        if (proposal.proposedValue() == null || proposal.proposedValue().signum() <= 0) {
            throw ApiException.badRequest("INVALID_VALUE", "가치 제안은 양수여야 합니다.");
        }
        GoldenAnchor anchor = anchors.findByName(proposal.citedAnchorName())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_ANCHOR",
                        "인용한 앵커가 없습니다: " + proposal.citedAnchorName()));

        double distance = Math.abs(log10(proposal.proposedValue()) - log10(anchor.getRefValue()));
        if (distance > props.value().grossLogDistance()) {
            log.warn("Gross 가치 오류 거부: name={}, proposed={}, anchor={}({}), log10 거리={}",
                    proposal.name(), proposal.proposedValue(), anchor.getName(), anchor.getRefValue(), distance);
            throw ApiException.badRequest("GROSS_VALUE_ERROR",
                    "제안 가치가 인용 앵커와 자릿수 단위로 어긋납니다.");
        }

        return items.save(new Item(proposal.name(), nameKey, proposal.description(), proposal.proposedValue(),
                proposal.genVersion(), proposal.genModel(), proposal.genPromptId(), anchor.getId()));
    }

    @Transactional
    public int invalidateCohort(String genVersion) {
        var cohort = items.findByGenVersion(genVersion);
        cohort.forEach(Item::invalidateCohort);
        log.info("코호트 무효화: genVersion={}, {}건", genVersion, cohort.size());
        return cohort.size();
    }

    @Transactional
    public void correct(Long itemId, BigDecimal correctedValue) {
        Item item = items.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "아이템이 없습니다."));
        item.correct(correctedValue);
    }

    static String normalizeKey(String name) {
        return name.strip().toLowerCase(Locale.KOREAN).replaceAll("\\s+", "-");
    }

    static double log10(BigDecimal value) {
        return Math.log10(value.doubleValue());
    }
}
