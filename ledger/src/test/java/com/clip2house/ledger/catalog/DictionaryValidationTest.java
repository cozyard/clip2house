package com.clip2house.ledger.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.support.IntegrationTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DictionaryValidationTest extends IntegrationTestBase {

    @Autowired ItemDictionaryService dictionary;
    @Autowired ItemRepository itemRepository;
    @Autowired GoldenAnchorRepository anchorRepository;

    private ItemDictionaryService.Proposal proposal(String name, long value, String anchorName, String genVersion) {
        return new ItemDictionaryService.Proposal(name, "설명", krw(value), anchorName,
                genVersion, "test-model", "prompt-1");
    }

    @Test
    void 자릿수가_어긋난_가치_제안은_골든_앵커_대조로_거부된다() {

        assertThatThrownBy(() -> dictionary.registerProposal(
                proposal("이상한 클립-" + UUID.randomUUID(), 800_000_000L, "클립", "gen-v9")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("자릿수");
    }

    @Test
    void 사전에_있는_아이템은_재계산_없이_재사용되고_뒤늦은_다른_제안값은_동결값을_바꾸지_못한다() {
        String name = "감성 캠핑 랜턴-" + UUID.randomUUID();
        Item first = dictionary.registerProposal(proposal(name, 40_000L, "백반 한 끼", "gen-v1"));
        Item second = dictionary.registerProposal(proposal(name, 70_000L, "무선 이어폰", "gen-v2"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getRefValue()).isEqualByComparingTo(krw(40_000L));
    }

    @Test
    void 불량_생성_버전은_코호트_단위로_무효화되고_앵커는_대상이_아니다() {
        String badVersion = "gen-bad-" + UUID.randomUUID().toString().substring(0, 8);
        Item bad1 = dictionary.registerProposal(proposal("불량품A-" + UUID.randomUUID(), 5_000L, "볼펜 세트", badVersion));
        Item bad2 = dictionary.registerProposal(proposal("불량품B-" + UUID.randomUUID(), 9_000L, "백반 한 끼", badVersion));
        Item good = dictionary.registerProposal(proposal("정상품-" + UUID.randomUUID(), 8_000L, "백반 한 끼", "gen-ok"));

        int invalidated = dictionary.invalidateCohort(badVersion);

        assertThat(invalidated).isEqualTo(2);
        assertThat(itemRepository.findById(bad1.getId()).orElseThrow().getValidationStatus())
                .isEqualTo(Item.ValidationStatus.COHORT_INVALIDATED);
        assertThat(itemRepository.findById(bad2.getId()).orElseThrow().getValidationStatus())
                .isEqualTo(Item.ValidationStatus.COHORT_INVALIDATED);
        assertThat(itemRepository.findById(good.getId()).orElseThrow().getValidationStatus())
                .isEqualTo(Item.ValidationStatus.VALID);

        assertThat(anchorRepository.count()).isEqualTo(11);
    }

    @Test
    void 무효화된_코호트는_공급_최근접_탐색에서_제외된다() {
        String badVersion = "gen-drift-" + UUID.randomUUID().toString().substring(0, 8);
        Item bad = dictionary.registerProposal(
                proposal("드리프트물건-" + UUID.randomUUID(), 12_345L, "백반 한 끼", badVersion));
        dictionary.invalidateCohort(badVersion);

        Item nearest = itemRepository.findNearestValid(Math.log10(12_345d)).orElseThrow();
        assertThat(nearest.getId()).isNotEqualTo(bad.getId());
    }

    @Test
    void 운영자_교정은_원값을_덮지_않고_교정값에_쓴다() {
        Item item = dictionary.registerProposal(
                proposal("교정대상-" + UUID.randomUUID(), 90_000L, "무선 이어폰", "gen-v3"));

        dictionary.correct(item.getId(), krw(120_000L));

        Item corrected = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(corrected.getRefValue()).isEqualByComparingTo(krw(90_000L));
        assertThat(corrected.getCorrectedValue()).isEqualByComparingTo(krw(120_000L));
        assertThat(corrected.effectiveValue()).isEqualByComparingTo(krw(120_000L));
    }
}
