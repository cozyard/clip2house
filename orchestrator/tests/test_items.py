import math

from app.config import settings
from app.items import generate_item
from app.providers import MockProvider

ANCHORS = [
    {"name": "클립", "value": 100},
    {"name": "백반 한 끼", "value": 10000},
    {"name": "최신 스마트폰", "value": 1000000},
    {"name": "서울 아파트", "value": 800000000},
]


async def test_생성_아이템은_앵커를_인용하고_provenance를_실어_보낸다():
    generated = await generate_item(MockProvider(), settings, target_log10_value=6.0,
                                    anchors=ANCHORS, existing_names=[], retry_limit=2)

    assert generated is not None
    assert generated["citedAnchorName"] in {a["name"] for a in ANCHORS}
    assert generated["genVersion"] == settings.gen_version
    assert generated["genModel"] == "mock"
    assert generated["genPromptId"]

    anchor_value = next(a["value"] for a in ANCHORS if a["name"] == generated["citedAnchorName"])
    distance = abs(math.log10(generated["proposedValue"]) - math.log10(anchor_value))
    assert distance <= 2.0


async def test_목표_가치대에_맞는_앵커를_고른다():
    low = await generate_item(MockProvider(), settings, 2.1, ANCHORS, [], 0)
    high = await generate_item(MockProvider(), settings, 8.7, ANCHORS, [], 0)

    assert low["citedAnchorName"] == "클립"
    assert high["citedAnchorName"] == "서울 아파트"
