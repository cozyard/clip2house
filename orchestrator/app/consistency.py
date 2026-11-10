"""페르소나 문체를 응답 경로 밖에서 점검한다. 결과는 감사 자료로만 기록한다."""

import asyncio
import logging
import re
from collections import defaultdict
from dataclasses import dataclass

from prometheus_client import Gauge

from .ledger import LedgerClient
from .personas import Persona

logger = logging.getLogger(__name__)

STYLE_DEVIATION = Gauge(
    "c2h_persona_style_deviation",
    "선언 문체 대비 관측 편차 (0에 가까울수록 일관적)",
    ["persona_id", "dimension"],
)

_EMOJI = re.compile(r"[\U0001F300-\U0001FAFF☀-➿]|[ㅋㅎ]{2,}|[ㅠㅜ]{2,}|[!?~]{2,}")

_JONDAE_END = re.compile(r"(요|니다|세요|습니까|가요|까요|어요|에요)[.!?~\sㅋㅎㅠㅜ]*$")


_EXPECTED_LENGTH = {"terse": 18.0, "normal": 40.0, "chatty": 80.0}


@dataclass(frozen=True)
class StyleStats:
    count: int
    avg_length: float
    emoji_rate: float
    jondae_rate: float


def measure(messages: list[str]) -> StyleStats:
    if not messages:
        return StyleStats(0, 0.0, 0.0, 0.0)
    lengths = [len(m) for m in messages]
    emoji_hits = sum(1 for m in messages if _EMOJI.search(m))
    jondae_hits = sum(1 for m in messages if _JONDAE_END.search(m.strip()))
    n = len(messages)
    return StyleStats(n, sum(lengths) / n, emoji_hits / n, jondae_hits / n)


def deviations(persona: Persona, stats: StyleStats) -> dict[str, float]:
    if stats.count == 0:
        return {}
    expected_length = _EXPECTED_LENGTH[persona.style_verbosity]
    length_dev = min(abs(stats.avg_length - expected_length) / expected_length, 1.0)
    emoji_dev = abs(stats.emoji_rate - persona.style_emoji)
    expected_jondae = 0.1 if persona.style_formality == "banmal" else 0.9
    formality_dev = abs(stats.jondae_rate - expected_jondae)
    return {"length": length_dev, "emoji": emoji_dev, "formality": formality_dev}


class ConsistencyChecker:
    WARN_THRESHOLD = 0.5
    MIN_SAMPLE = 5

    def __init__(self, ledger: LedgerClient, personas: dict[int, Persona], interval_s: float) -> None:
        self._ledger = ledger
        self._personas = personas
        self._interval_s = interval_s
        self._task: asyncio.Task | None = None

    def start(self) -> None:
        if self._interval_s > 0:
            self._task = asyncio.get_running_loop().create_task(self._loop())

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()

    async def _loop(self) -> None:
        while True:
            try:
                await self.check_once()
            except asyncio.CancelledError:
                return
            except Exception as exc:
                logger.warning("consistency 검증 실패: %s", exc)
            await asyncio.sleep(self._interval_s)

    async def check_once(self) -> dict[int, dict[str, float]]:
        samples = await self._ledger.get_persona_samples()
        grouped: dict[int, list[str]] = defaultdict(list)
        for sample in samples:
            grouped[sample["personaId"]].append(sample["content"])

        results: dict[int, dict[str, float]] = {}
        for persona_id, messages in grouped.items():
            persona = self._personas.get(persona_id)
            if persona is None or len(messages) < self.MIN_SAMPLE:
                continue
            devs = deviations(persona, measure(messages))
            results[persona_id] = devs
            for dimension, value in devs.items():
                STYLE_DEVIATION.labels(persona_id=str(persona_id), dimension=dimension).set(value)
                if value > self.WARN_THRESHOLD:
                    logger.warning("페르소나 %s 문체 이탈: %s=%.2f (표본 %d)",
                                   persona.nickname, dimension, value, len(messages))
        return results
