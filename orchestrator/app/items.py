import logging
from typing import Any

from .config import Settings
from .providers import Provider, ProviderError
from .schemas import GeneratedItem, item_json_schema

logger = logging.getLogger(__name__)

_PROMPT_ID = "item-gen-v1"

_SYSTEM = """당신은 한국 중고거래 앱에 올라올 법한 매물을 만드는 작가다.
목표 가치대(log10 원)가 주어지면 그 값어치에 어울리는 현실적인 물건 하나를 지어낸다.

규칙:
- 이름은 짧고 구체적으로(브랜드는 지어내도 좋다). 이미 있는 이름과 겹치지 않게.
- 설명은 중고거래 말투로 1~2문장.
- proposed_value는 그 물건의 현실적인 원화 가치.
- cited_anchor_name은 제안 가치와 가장 비슷한 값어치의 앵커를 고른다."""


async def generate_item(provider: Provider, settings: Settings,
                        target_log10_value: float, anchors: list[dict[str, Any]],
                        existing_names: list[str], retry_limit: int) -> dict[str, Any] | None:
    anchor_names = [anchor["name"] for anchor in anchors]
    schema = item_json_schema(anchor_names)
    user = (
        f"목표 가치대: 10^{target_log10_value:.2f} 원 부근\n"
        f"앵커 목록: {', '.join(f'{a['name']}({a['value']}원)' for a in anchors)}\n"
        f"피해야 할 기존 이름: {', '.join(existing_names[:50]) or '(없음)'}"
    )
    context = {"kind": "item", "targetLog10Value": target_log10_value, "anchors": anchors}

    for _ in range(1 + retry_limit):
        try:
            raw = await provider.complete_structured(_SYSTEM, [{"role": "user", "content": user}], schema, context)
            item = GeneratedItem.model_validate(raw)
        except (ProviderError, ValueError) as exc:
            logger.warning("아이템 생성 실패, 재시도: %s", exc)
            continue
        return {
            "name": item.name,
            "description": item.description,
            "proposedValue": item.proposed_value,
            "citedAnchorName": item.cited_anchor_name,
            "genVersion": settings.gen_version,
            "genModel": provider.name,
            "genPromptId": _PROMPT_ID,
        }
    return None
