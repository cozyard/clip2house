"""NVIDIA Nemotron-Personas-Korea(CC BY 4.0)를 성별·연령대로 층화 추출한다. 출처와 실행 방법은 orchestrator/README.md를 참조한다."""

import argparse
import hashlib
import json
import random
import sys
import urllib.parse
import urllib.request
from pathlib import Path

DATASET = "nvidia/Nemotron-Personas-Korea"
API = "https://datasets-server.huggingface.co/rows"
PAGE_SIZE = 100  # datasets-server 최대 length


NICK_HEADS = ["동네", "골목", "성실한", "느긋한", "야무진", "부지런한", "조용한", "씩씩한",
              "다정한", "무던한", "알뜰한", "호기심많은", "단골", "새벽", "주말", "퇴근길"]
NICK_TAILS = ["거래러", "이웃", "장터지기", "수집가", "정리왕", "살림꾼", "나눔러", "단골손님",
              "박스채", "직거래파", "택배파", "흥정꾼", "구경꾼", "맥시멀리스트", "미니멀리스트", "옷장정리중"]


def fetch_rows(offset: int, length: int) -> list[dict]:
    query = urllib.parse.urlencode({
        "dataset": DATASET, "config": "default", "split": "train",
        "offset": offset, "length": length,
    })
    with urllib.request.urlopen(f"{API}?{query}", timeout=60) as response:
        payload = json.load(response)
    return [entry["row"] for entry in payload["rows"]]


def stable_unit(uuid: str, salt: str) -> float:
    digest = hashlib.sha256(f"{uuid}:{salt}".encode()).digest()
    return int.from_bytes(digest[:8]) / 2**64


def age_band(age: int) -> str:
    decade = min(max(age // 10, 1), 6)
    return f"{decade}0s"


def to_game_persona(index: int, row: dict) -> dict:
    uuid = row["uuid"]
    rng = random.Random(uuid)
    nickname = rng.choice(NICK_HEADS) + rng.choice(NICK_TAILS) + str(1 + int(stable_unit(uuid, "nick") * 98))

    formality_roll = stable_unit(uuid, "formality")

    age_lean = min(row["age"], 70) / 70
    formality = ("formal" if formality_roll * 0.5 + age_lean * 0.5 > 0.62
                 else "banmal" if formality_roll * 0.5 + age_lean * 0.5 < 0.33
                 else "casual-jondae")

    verbosity_roll = stable_unit(uuid, "verbosity")
    verbosity = "terse" if verbosity_roll < 0.33 else "chatty" if verbosity_roll > 0.72 else "normal"

    return {
        "id": index,
        "source_uuid": uuid,
        "nickname": nickname,
        "gender": "M" if row["sex"] == "남자" else "F",
        "age_band": age_band(row["age"]),
        "region": row.get("province") or "미상",
        "summary": row["persona"],
        "generosity": round(stable_unit(uuid, "generosity"), 2),
        "price_sensitivity": round(stable_unit(uuid, "price"), 2),
        "patience": round(stable_unit(uuid, "patience"), 2),
        "style_formality": formality,
        "style_emoji": round(stable_unit(uuid, "emoji") * (1.0 - age_lean * 0.5), 2),
        "style_verbosity": verbosity,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pool-size", type=int, default=400, help="원본에서 받아올 행 수")
    parser.add_argument("--sample-size", type=int, default=64, help="층화 추출할 페르소나 수")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=Path, default=Path("data/personas_kr.json"))
    args = parser.parse_args()

    rows: list[dict] = []
    for offset in range(0, args.pool_size, PAGE_SIZE):
        rows.extend(fetch_rows(offset, min(PAGE_SIZE, args.pool_size - offset)))
    print(f"원본 {len(rows)}행 수신", file=sys.stderr)

    strata: dict[tuple[str, str], list[dict]] = {}
    for row in rows:
        key = (row["sex"], age_band(row["age"]))
        strata.setdefault(key, []).append(row)

    rng = random.Random(args.seed)
    picked: list[dict] = []
    for key in sorted(strata):
        members = strata[key]
        quota = max(1, round(args.sample_size * len(members) / len(rows)))
        picked.extend(rng.sample(members, min(quota, len(members))))
    picked = picked[:args.sample_size]

    personas = [to_game_persona(i + 1, row) for i, row in enumerate(picked)]
    output = {
        "attribution": (
            "Sampled from NVIDIA Nemotron-Personas-Korea "
            "(https://huggingface.co/datasets/nvidia/Nemotron-Personas-Korea), CC BY 4.0. "
            "인물 요약·성별·연령·지역은 원본이며, 닉네임과 게임용 속성(협상 성향, 문체 파라미터)은 "
            "원본 uuid 해시로 결정적으로 부여한 파생값이다. 생성 스크립트: scripts/sample_personas.py"
        ),
        "source_dataset": DATASET,
        "sample_seed": args.seed,
        "personas": personas,
    }
    args.out.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    bands = sorted({(p["gender"], p["age_band"]) for p in personas})
    print(f"{len(personas)}명 저장 -> {args.out} (층: {bands})", file=sys.stderr)


if __name__ == "__main__":
    main()
