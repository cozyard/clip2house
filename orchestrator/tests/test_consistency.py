from app.consistency import ConsistencyChecker, deviations, measure

from .conftest import FakeLedger


def test_문체_측정은_길이_이모티콘_존대를_읽는다():
    stats = measure([
        "안녕하세요! 물건 아직 있나요?",
        "네네 가능해요 ㅎㅎㅎ",
        "내일 저녁에 뵐게요~!!",
    ])
    assert stats.count == 3
    assert stats.avg_length > 0
    assert stats.emoji_rate > 0.5
    assert stats.jondae_rate == 1.0


def test_선언과_어긋난_문체는_편차가_크다(persona):
    stats = measure(["ㅇㅇ 그거 팔았음", "낼 봐", "됐고 다른 거 없냐", "그건 안 바꿈", "글쎄"])
    devs = deviations(persona, stats)
    assert devs["formality"] > 0.5
    assert devs["emoji"] <= 0.5


async def test_주기_검증은_편차를_계산하고_지표로_노출한다(persona):
    ledger = FakeLedger()
    ledger.persona_samples = [
        {"personaId": persona.id, "content": "안녕하세요! 가능해요 ㅎㅎ"},
        {"personaId": persona.id, "content": "네 내일 봬요~"},
        {"personaId": persona.id, "content": "좋아요, 그 조건이면 바꿔요!"},
        {"personaId": persona.id, "content": "음 조금만 더 생각해볼게요"},
        {"personaId": persona.id, "content": "사진 한 장만 더 보내주세요!"},
    ]
    checker = ConsistencyChecker(ledger, {persona.id: persona}, interval_s=0)

    results = await checker.check_once()

    assert persona.id in results
    assert set(results[persona.id]) == {"length", "emoji", "formality"}

    assert results[persona.id]["formality"] < 0.3


async def test_표본이_적은_페르소나는_판정하지_않는다(persona):
    ledger = FakeLedger()
    ledger.persona_samples = [{"personaId": persona.id, "content": "안녕하세요"}]
    checker = ConsistencyChecker(ledger, {persona.id: persona}, interval_s=0)

    assert await checker.check_once() == {}
