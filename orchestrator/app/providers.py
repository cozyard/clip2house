import hashlib
import json
import logging
import time
from abc import ABC, abstractmethod
from typing import Any

import httpx

logger = logging.getLogger(__name__)


class ProviderError(Exception):
    """호출 실패와 출력 오류를 턴 재시도 경로로 전달한다."""


class Provider(ABC):
    name: str

    @abstractmethod
    async def complete_structured(
        self, system: str, messages: list[dict[str, Any]], schema: dict, context: dict[str, Any]
    ) -> dict[str, Any]:
        """스키마에 맞는 객체를 반환한다. context는 모의 응답을 재현하는 데 사용한다."""

    async def close(self) -> None:
        pass


class NvidiaProvider(Provider):
    name = "nvidia"

    def __init__(self, api_key: str, model: str, base_url: str = "https://integrate.api.nvidia.com/v1") -> None:
        self._model = model
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/") + "/",
            headers={"Authorization": f"Bearer {api_key}"},
            timeout=httpx.Timeout(30.0, connect=5.0),
        )

    async def close(self) -> None:
        await self._client.aclose()

    async def complete_structured(
        self, system: str, messages: list[dict[str, Any]], schema: dict, context: dict[str, Any]
    ) -> dict[str, Any]:
        body = {
            "model": self._model,
            "messages": [{"role": "system", "content": system}, *messages],
            "max_tokens": 2048,
            "stream": False,
            "tools": [{"type": "function", "function": {
                "name": "respond",
                "description": "요청한 결과를 지정된 형식으로 제출한다.",
                "parameters": schema,
            }}],
            "tool_choice": {"type": "function", "function": {"name": "respond"}},
        }
        if self._model.startswith("nvidia/nemotron-3.5-"):
            body["chat_template_kwargs"] = {"enable_thinking": False}
        try:
            response = await self._client.post("chat/completions", json=body)
            response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            raise ProviderError(f"nvidia 호출 실패: HTTP {exc.response.status_code}") from exc
        except httpx.RequestError as exc:
            raise ProviderError(f"nvidia 연결 실패: {type(exc).__name__}") from exc
        try:
            choice = response.json()["choices"][0]
            if not isinstance(choice, dict):
                raise ValueError("응답 선택지가 객체가 아닙니다")
            if choice.get("finish_reason") in {"length", "content_filter"}:
                raise ValueError("응답이 완료되지 않았습니다")
            calls = choice["message"]["tool_calls"]
            if len(calls) != 1 or calls[0]["type"] != "function" or calls[0]["function"]["name"] != "respond":
                raise ValueError("지정된 함수 응답이 아닙니다")
            result = json.loads(calls[0]["function"]["arguments"])
            if not isinstance(result, dict):
                raise ValueError("응답이 객체가 아닙니다")
            return result
        except (ValueError, KeyError, IndexError, TypeError) as exc:
            raise ProviderError("nvidia 구조화 응답 형식이 올바르지 않습니다") from exc


class MockProvider(Provider):
    name = "mock"

    async def complete_structured(
        self, system: str, messages: list[dict[str, Any]], schema: dict, context: dict[str, Any]
    ) -> dict[str, Any]:
        if context.get("kind") == "item":
            return self._generate_item(context)
        if context.get("kind") == "classify":
            return {"classification": "NONE"}
        return self._negotiate(context)

    def _negotiate(self, context: dict[str, Any]) -> dict[str, Any]:
        generosity = context.get("persona", {}).get("generosity", 0.5)
        player_offers = [
            offer for offer in context.get("standingOffers", []) if offer.get("proposer") == "PLAYER"
        ]
        if player_offers:
            offer = player_offers[0]
            ratio = float(offer.get("valueRatio", 0))

            threshold = 1.0 - generosity * 0.7
            if ratio >= threshold:
                return {
                    "action": "ACCEPT",
                    "messages": ["좋아요, 그 조건이면 바꿔요. 거래해요!"],
                    "accept_offer_id": offer["offerId"],
                }
            return {
                "action": "CONTINUE",
                "messages": ["음… 그건 좀 아쉬운데요. 다른 물건은 없으세요?"],
            }
        return {
            "action": "CONTINUE",
            "messages": ["안녕하세요! 어떤 물건이랑 바꾸실 생각이세요?"],
        }

    def _generate_item(self, context: dict[str, Any]) -> dict[str, Any]:
        target_log = float(context["targetLog10Value"])
        anchors = context["anchors"]
        nearest = min(anchors, key=lambda a: abs(_log10(a["value"]) - target_log))
        value = max(1, int(round(10 ** target_log)))

        digest = hashlib.sha256(f"{target_log:.4f}:{time.time_ns()}".encode()).hexdigest()[:6]
        name = f"{nearest['name']} 급 매물 {digest}"
        return {
            "name": name,
            "description": f"{nearest['name']}와 비슷한 값어치의 물건입니다. 상태 양호합니다.",
            "proposed_value": value,
            "cited_anchor_name": nearest["name"],
        }


def _log10(value: Any) -> float:
    import math

    return math.log10(float(value))


class ProviderRegistry:
    def __init__(self, providers: list[Provider], offline_seconds: float) -> None:
        if not providers:
            raise ValueError("최소 하나의 Provider가 필요합니다")
        self._providers = providers
        self._offline_seconds = offline_seconds
        self._offline_until: dict[int, float] = {}

    def provider_for(self, persona_id: int) -> Provider:
        digest = hashlib.sha256(str(persona_id).encode()).digest()
        return self._providers[int.from_bytes(digest[:4]) % len(self._providers)]

    def is_offline(self, persona_id: int) -> bool:
        until = self._offline_until.get(persona_id)
        return until is not None and until > time.monotonic()

    def mark_offline(self, persona_id: int) -> None:
        self._offline_until[persona_id] = time.monotonic() + self._offline_seconds
        logger.info("페르소나 %s Offline Degrade (%.0f초)", persona_id, self._offline_seconds)
