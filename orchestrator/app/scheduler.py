"""대기 중 새 입력이 오면 턴을 다시 예약한다. 타이머는 프로세스에 귀속되므로 단일 인스턴스에서 실행한다."""

import asyncio
import logging
import random
from typing import Awaitable, Callable

from .personas import Persona

logger = logging.getLogger(__name__)


class QuietWindowScheduler:
    def __init__(self, run_turn: Callable[[int], Awaitable[None]],
                 personas: dict[int, Persona], window_min_s: float, window_max_s: float) -> None:
        self._run_turn = run_turn
        self._personas = personas
        self._window_min_s = window_min_s
        self._window_max_s = window_max_s
        self._pending: dict[int, asyncio.Task] = {}

    def notify(self, negotiation_id: int, persona_id: int) -> None:
        existing = self._pending.pop(negotiation_id, None)
        if existing is not None and not existing.done():
            existing.cancel()

        delay = self._sample_window(persona_id)
        task = asyncio.get_running_loop().create_task(self._fire_after(negotiation_id, delay))
        self._pending[negotiation_id] = task

    def _sample_window(self, persona_id: int) -> float:
        persona = self._personas.get(persona_id)
        if persona is None:
            return random.uniform(self._window_min_s, self._window_max_s)
        low, high = persona.quiet_window_s(self._window_min_s, self._window_max_s)
        return random.uniform(low, high)

    async def _fire_after(self, negotiation_id: int, delay: float) -> None:
        try:
            await asyncio.sleep(delay)
        except asyncio.CancelledError:
            return
        self._pending.pop(negotiation_id, None)
        try:
            await self._run_turn(negotiation_id)
        except Exception:
            logger.exception("턴 처리 중 예외: negotiation=%s", negotiation_id)

    async def shutdown(self) -> None:
        for task in self._pending.values():
            task.cancel()
        self._pending.clear()
