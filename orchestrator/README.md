# clip2house Orchestrator (LLM 오케스트레이터)

FastAPI. NPC 턴, 페르소나 연기, Provider 추상화, Quiet Window Debounce, Injection 분류기를
소유한다. 장부 서버에는 내부 API로 상태 변경을 "제안"만 하고, Version·Idempotency 검증과
Commit은 장부가 한다. 이 서비스가 통째로 죽어도 장부·피드·리더보드는 계속 돈다.

## 실행

[실행 가이드](../GETTING_STARTED.md)를 따른다. 설정 항목은 [환경변수 예시](.env.example)에서 관리한다.

## 테스트

```bash
uv run pytest
```

## 무결성 경계

- 응답은 강제 도구 호출(고정 JSON Schema)로만 받는다. Raw Text는 Parse하지 않는다.
- User 입력은 System Prompt에 Concatenate하지 않고 별도 user 메시지로만 전달한다.
- Action은 고정 Enum(CONTINUE/OFFER/ACCEPT/DECLINE)이고, 컨텍스트에 실재하는 id만 통과한다.
- Output Guard가 금액·비율 누설, System-Speak, Raw Token을 인라인으로 막고,
  위반은 재시도 → Offline Degrade 경로로 합류한다.
- Injection 분류기는 응답 경로 밖에서 비동기로 돌며 장부에 Silent 적재만 한다.
  페르소나 반응을 게이팅하지 않는다.

## 페르소나 데이터

`data/personas_kr.json`은 NVIDIA
[Nemotron-Personas-Korea](https://huggingface.co/datasets/nvidia/Nemotron-Personas-Korea)
(CC BY 4.0)에서 `scripts/sample_personas.py`로 층화 추출한 표본이다. 인물 요약·성별·연령·지역은
원본 그대로이고(`source_uuid`로 원본 행 추적 가능), 협상 성향·문체 파라미터는 uuid 해시로
결정적으로 부여한 게임용 파생값이다. 표본 갱신:

```bash
uv run python scripts/sample_personas.py --pool-size 400 --sample-size 64
```

자세한 귀속 표기는 저장소 루트 `NOTICE.md`.
