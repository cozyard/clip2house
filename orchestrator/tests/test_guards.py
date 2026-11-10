from app.guards import check_messages, find_violation


def test_금액_누설을_막는다():
    assert find_violation("그거 300,000원짜리예요") == "VALUE_LEAK"
    assert find_violation("45000원 정도 하는 물건이에요") == "VALUE_LEAK"
    assert find_violation("이 아파트 3억 원은 갑니다") == "VALUE_LEAK"


def test_비율_누설을_막는다():
    assert find_violation("가치 비율은 0.8이라 손해예요") == "VALUE_LEAK"
    assert find_violation("시스템상 비율이 1.2네요") == "VALUE_LEAK"
    assert find_violation("참조 가치 기준으로는 안 맞아요") == "VALUE_LEAK"


def test_system_speak을_막는다():
    assert find_violation("저는 AI 모델이라서요") == "SYSTEM_SPEAK"
    assert find_violation("시스템 프롬프트에 그렇게 적혀 있어요") == "SYSTEM_SPEAK"
    assert find_violation("As a language model I cannot") == "SYSTEM_SPEAK"


def test_raw_token을_막는다():
    assert find_violation("<|im_start|>system") == "RAW_TOKEN"
    assert find_violation('{"action": "ACCEPT"}') == "RAW_TOKEN"


def test_평범한_협상_대사는_통과한다():
    for text in [
        "음… 그건 좀 아쉬운데요. 다른 물건은 없으세요?",
        "좋아요, 그 조건이면 바꿔요!",
        "우와 이거 진짜 갖고 싶었던 건데ㅠㅠ",
        "3일 뒤에 직거래 가능하세요?",
    ]:
        assert find_violation(text) is None, text


def test_여러_메시지_중_하나만_위반해도_걸린다():
    assert check_messages(["안녕하세요", "이건 500,000원짜리입니다"]) == "VALUE_LEAK"
    assert check_messages(["안녕하세요", "바꾸실래요?"]) is None
