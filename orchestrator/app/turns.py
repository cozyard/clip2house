import asyncio
import logging
import uuid
from typing import Any

from prometheus_client import Counter

from . import guards
from .classifier import classify, classify_llm
from .ledger import LedgerClient
from .personas import Persona
from .prompts import build_system_prompt, build_user_messages
from .providers import ProviderError, ProviderRegistry
from .schemas import TURN_ACTION_JSON_SCHEMA, TurnAction

logger = logging.getLogger(__name__)

TURNS_TOTAL = Counter("c2h_turns_total", "처리한 턴 수", ["result"])
PROVIDER_RETRIES = Counter("c2h_provider_retries_total", "Provider 재시도 수")
GUARD_VIOLATIONS = Counter("c2h_guard_violations_total", "Output Guard 위반", ["kind"])
CLASSIFIER_HITS = Counter("c2h_classifier_hits_total", "Injection 분류기 Hit", ["kind"])


class TurnPipeline:
    def __init__(self, ledger: LedgerClient, registry: ProviderRegistry,
                 personas: dict[int, Persona], retry_limit: int,
                 llm_classifier: bool = False) -> None:
        self._ledger = ledger
        self._registry = registry
        self._personas = personas
        self._retry_limit = retry_limit
        self._llm_classifier = llm_classifier

    async def run_turn(self, negotiation_id: int) -> None:
        try:
            context = await self._ledger.get_context(negotiation_id)
        except Exception as exc:
            logger.warning("컨텍스트 조회 실패, 턴 스킵: negotiation=%s %s", negotiation_id, exc)
            TURNS_TOTAL.labels(result="context_error").inc()
            return

        if context.get("status") != "ACTIVE":
            TURNS_TOTAL.labels(result="closed").inc()
            return

        persona_id = context["personaId"]
        persona = self._personas.get(persona_id)
        if persona is None:
            logger.error("모르는 페르소나 %s, 턴 스킵", persona_id)
            return
        if self._registry.is_offline(persona_id):
            TURNS_TOTAL.labels(result="offline").inc()
            return

        asyncio.create_task(self._classify_and_report(context))

        action = await self._produce_action(persona, context, negotiation_id)
        if action is None:
            return

        await self._submit(negotiation_id, context, action)

    async def _produce_action(self, persona: Persona, context: dict[str, Any],
                              negotiation_id: int) -> TurnAction | None:
        provider = self._registry.provider_for(persona.id)
        system = build_system_prompt(persona, context)
        messages = build_user_messages(context)

        last_problem = ""
        for attempt in range(1 + self._retry_limit):
            if attempt > 0:
                PROVIDER_RETRIES.inc()
            try:
                raw = await provider.complete_structured(
                    system, messages, TURN_ACTION_JSON_SCHEMA, context)
                action = TurnAction.model_validate(raw)
            except (ProviderError, ValueError) as exc:
                last_problem = f"provider/schema: {exc}"
                continue

            problem = self._validate_against_context(action, context)
            if problem:
                last_problem = problem
                continue

            violation = guards.check_messages(action.messages)
            if violation:
                GUARD_VIOLATIONS.labels(kind=violation).inc()
                last_problem = f"guard: {violation}"
                continue

            TURNS_TOTAL.labels(result="ok").inc()
            return action

        logger.warning("턴 실패로 Offline Degrade: persona=%s (%s)", persona.id, last_problem)
        self._registry.mark_offline(persona.id)
        await self._ledger.report_offline_degrade(persona.id, last_problem[:200], negotiation_id)
        TURNS_TOTAL.labels(result="degraded").inc()
        return None

    @staticmethod
    def _validate_against_context(action: TurnAction, context: dict[str, Any]) -> str | None:
        if action.action == "OFFER":
            holding_ids = {holding["holdingId"] for holding in context.get("playerHoldings", [])}
            if action.offer_holding_id not in holding_ids:
                return f"OFFER가 실재하지 않는 holding을 지목: {action.offer_holding_id}"
        if action.action == "ACCEPT":
            player_offer_ids = {
                offer["offerId"] for offer in context.get("standingOffers", [])
                if offer["proposer"] == "PLAYER"
            }
            if action.accept_offer_id not in player_offer_ids:
                return f"ACCEPT가 테이블 위에 없는 offer를 지목: {action.accept_offer_id}"
        return None

    async def _submit(self, negotiation_id: int, context: dict[str, Any], action: TurnAction) -> None:
        payload: dict[str, Any] = {
            "action": action.action,
            "messages": action.messages,
            "turnId": f"turn-{uuid.uuid4()}",
        }
        if action.action == "OFFER":
            payload["offerHoldingId"] = action.offer_holding_id
        if action.action == "ACCEPT":
            offer = next(o for o in context["standingOffers"] if o["offerId"] == action.accept_offer_id)
            payload["acceptOfferId"] = action.accept_offer_id

            payload["expectedListingVersion"] = context["listing"]["listingVersion"]
            payload["expectedHoldingVersion"] = offer["playerHoldingVersion"]

        try:
            response = await self._ledger.post_action(negotiation_id, payload)
        except Exception as exc:
            logger.warning("액션 제출 실패: negotiation=%s %s", negotiation_id, exc)
            return
        if response.status_code == 409:
            logger.info("액션이 충돌로 무효: negotiation=%s %s", negotiation_id, response.text[:200])
        elif response.status_code >= 400:
            logger.warning("액션 거부됨: negotiation=%s status=%s %s",
                           negotiation_id, response.status_code, response.text[:200])

    async def _classify_and_report(self, context: dict[str, Any]) -> None:
        for message in context.get("unreadPlayerMessages", []):
            label = classify(message["content"])
            if label is None and self._llm_classifier:
                provider = self._registry.provider_for(context["personaId"])
                label = await classify_llm(provider, message["content"])
            if label is None:
                continue
            CLASSIFIER_HITS.labels(kind=label).inc()
            await self._ledger.report_injection({
                "playerId": context.get("playerId"),
                "personaId": context.get("personaId"),
                "negotiationId": context.get("negotiationId"),
                "rawInput": message["content"],
                "classifiedType": label,
                "botResponse": None,
            })
