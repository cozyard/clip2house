import logging
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class LedgerClient:
    def __init__(self, base_url: str, secret: str) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url,
            headers={"X-Internal-Secret": secret},
            timeout=httpx.Timeout(10.0, connect=3.0),
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def sync_personas(self, personas: list[dict[str, Any]]) -> None:
        response = await self._client.put("/internal/personas", json={"personas": personas})
        response.raise_for_status()

    async def get_context(self, negotiation_id: int) -> dict[str, Any]:
        response = await self._client.get(f"/internal/negotiations/{negotiation_id}/context")
        response.raise_for_status()
        return response.json()

    async def post_action(self, negotiation_id: int, payload: dict[str, Any]) -> httpx.Response:
        return await self._client.post(f"/internal/negotiations/{negotiation_id}/actions", json=payload)

    async def report_injection(self, payload: dict[str, Any]) -> None:
        try:
            await self._client.post("/internal/audit/injection", json=payload)
        except httpx.HTTPError as exc:
            logger.warning("injection 감사 적재 실패: %s", exc)

    async def get_persona_samples(self, limit: int = 300) -> list[dict[str, Any]]:
        response = await self._client.get("/internal/messages/persona-samples", params={"limit": limit})
        response.raise_for_status()
        return response.json()

    async def report_offline_degrade(self, persona_id: int, reason: str, negotiation_id: int | None) -> None:
        try:
            await self._client.post(
                "/internal/audit/offline-degrade",
                json={"personaId": persona_id, "reason": reason, "negotiationId": negotiation_id},
            )
        except httpx.HTTPError as exc:
            logger.warning("offline degrade 보고 실패: %s", exc)
