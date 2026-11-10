import asyncio

from app.scheduler import QuietWindowScheduler


async def test_quiet_window는_burst를_한_번의_턴으로_debounce한다(persona):
    fired: list[int] = []

    async def run_turn(negotiation_id: int) -> None:
        fired.append(negotiation_id)

    scheduler = QuietWindowScheduler(run_turn, {persona.id: persona},
                                     window_min_s=0.03, window_max_s=0.05)

    for _ in range(5):
        scheduler.notify(100, persona.id)
        await asyncio.sleep(0.005)

    await asyncio.sleep(0.15)
    assert fired == [100]


async def test_협상마다_창이_독립적으로_돈다(persona):
    fired: list[int] = []

    async def run_turn(negotiation_id: int) -> None:
        fired.append(negotiation_id)

    scheduler = QuietWindowScheduler(run_turn, {persona.id: persona},
                                     window_min_s=0.02, window_max_s=0.03)
    scheduler.notify(1, persona.id)
    scheduler.notify(2, persona.id)

    await asyncio.sleep(0.12)
    assert sorted(fired) == [1, 2]


async def test_shutdown은_대기_중인_턴을_취소한다(persona):
    fired: list[int] = []

    async def run_turn(negotiation_id: int) -> None:
        fired.append(negotiation_id)

    scheduler = QuietWindowScheduler(run_turn, {persona.id: persona},
                                     window_min_s=0.05, window_max_s=0.06)
    scheduler.notify(1, persona.id)
    await scheduler.shutdown()

    await asyncio.sleep(0.1)
    assert fired == []
