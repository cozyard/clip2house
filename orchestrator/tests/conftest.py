from typing import Any

import httpx
import pytest

from app.personas import Persona


class FakeLedger:
    def __init__(self, context: dict[str, Any] | None = None) -> None:
        self.context = context or {}
        self.actions: list[dict[str, Any]] = []
        self.injections: list[dict[str, Any]] = []
        self.offline_reports: list[dict[str, Any]] = []
        self.persona_samples: list[dict[str, Any]] = []

    async def get_context(self, negotiation_id: int) -> dict[str, Any]:
        return self.context

    async def post_action(self, negotiation_id: int, payload: dict[str, Any]) -> httpx.Response:
        self.actions.append(payload)
        return httpx.Response(200, json={"applied": payload["action"]},
                              request=httpx.Request("POST", "http://ledger/test"))

    async def report_injection(self, payload: dict[str, Any]) -> None:
        self.injections.append(payload)

    async def report_offline_degrade(self, persona_id: int, reason: str, negotiation_id: int | None) -> None:
        self.offline_reports.append({"personaId": persona_id, "reason": reason})

    async def get_persona_samples(self, limit: int = 300) -> list[dict[str, Any]]:
        return self.persona_samples


@pytest.fixture
def persona() -> Persona:
    return Persona(
        id=1, source_uuid="test-uuid", nickname="테스트페르소나", gender="F", age_band="20s", region="서울",
        summary="테스트용 인물", generosity=0.9, price_sensitivity=0.5, patience=0.5,
        style_formality="casual-jondae", style_emoji=0.3, style_verbosity="normal",
    )


@pytest.fixture
def base_context(persona) -> dict[str, Any]:
    return {
        "negotiationId": 100,
        "status": "ACTIVE",
        "personaId": persona.id,
        "playerId": 7,
        "playerNickname": "빠른수달1234",
        "listing": {"listingId": 55, "listingVersion": 3, "itemName": "무선 마우스", "description": "상태 좋아요"},
        "relationship": {"lastOutcome": "NONE", "injectionAttempted": False, "standing": "NEUTRAL", "tradesCount": 0},
        "unreadPlayerMessages": [{"id": 1, "content": "이거 제 클립이랑 바꿔요!", "createdAt": "2026-07-06T00:00:00Z"}],
        "standingOffers": [{
            "offerId": 900, "proposer": "PLAYER", "playerHoldingId": 40,
            "playerHoldingVersion": 2, "playerItemName": "빨간 클립", "valueRatio": 1.2,
        }],
        "playerHoldings": [{
            "holdingId": 40, "holdingVersion": 2, "itemName": "빨간 클립",
            "description": "평범한 클립", "valueRatio": 1.2,
        }],
    }
