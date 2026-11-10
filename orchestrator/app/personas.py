import json
import random
from dataclasses import dataclass
from pathlib import Path

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "personas_kr.json"


@dataclass(frozen=True)
class Persona:
    id: int
    source_uuid: str
    nickname: str
    gender: str
    age_band: str
    region: str
    summary: str
    generosity: float
    price_sensitivity: float  # 0 ~ 1: 비율의 작은 차이에 반응하는 해상도
    patience: float
    style_formality: str
    style_emoji: float
    style_verbosity: str

    def quiet_window_s(self, min_s: float, max_s: float) -> tuple[float, float]:
        span = max_s - min_s
        center = min_s + span * (0.2 + 0.6 * self.patience)
        low = max(min_s, center - span * 0.15)
        high = min(max_s, center + span * 0.15)
        return (low, high)


def load_personas(count: int, seed: int, path: Path = DATA_PATH) -> list[Persona]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    pool = [Persona(**entry) for entry in raw["personas"]]
    if count >= len(pool):
        return pool

    rng = random.Random(seed)
    strata: dict[tuple[str, str], list[Persona]] = {}
    for persona in pool:
        strata.setdefault((persona.gender, persona.age_band), []).append(persona)

    picked: list[Persona] = []
    for key in sorted(strata):
        members = sorted(strata[key], key=lambda p: p.id)
        quota = max(1, round(count * len(members) / len(pool)))
        picked.extend(rng.sample(members, min(quota, len(members))))
    picked = picked[:count]
    if len(picked) < count:
        remaining = [p for p in sorted(pool, key=lambda p: p.id) if p not in picked]
        picked.extend(rng.sample(remaining, count - len(picked)))
    return sorted(picked, key=lambda p: p.id)
