<div align="center">
  <img src="./static/image/c2h.png" alt="One Clip To House" width="50%">
</div>

## 📝 Overview

빨간 클립 하나로 시작하는 물물교환 시뮬레이션 게임이다.  
상대는 전부 LLM이 연기하는 사람들이고, 채팅으로 흥정해 거래한다.

## 🧠 Design Philosophy

### 1. 제약을 현실로 치환합니다.

```
LLM의 기술적 제약을 결함이 아니라 현실성으로 치환한다.

Latency → 사람은 바로 답장하지 않는다.
Quiet Window, Batching → 사람은 밀린 메시지를 몰아 읽고 한 번에 답한다.
Rate Limit, Provider Down → 사람은 자리를 비우거나, 의도적으로 피할 수 있다.
```

### 2. 무결성은 구조로, 설득은 전략으로 보장합니다.

```
지켜야 할 대상은 서버 소유 상태와 구조화된 거래다.
프롬프트 인젝션은 막아야 할 위협이 아니라 핵심 플레이다.
```

### 3. 시스템은 사실을, 페르소나는 판단을 보장합니다.

```
시스템은 동결된 가치와 거래 비율 같은 사실을 쥐고, 페르소나는 그걸 받아들일지 판단한다.
```

## 🗂️ Documentation

| #   | 문서                                                                     | 내용                                             |
| --- | ------------------------------------------------------------------------ | ------------------------------------------------ |
| 1   | [Game Design](static/docs/01-game-design.md)                             | 규칙, 가치, 시장, 진행, 리더보드                 |
| 2   | [Integrity & Concurrency](static/docs/02-integrity-concurrency.md)       | 오염 불가 경계와 경합 정합성                     |
| 3   | [Opponents (NPC)](static/docs/03-opponents-npc.md)                       | 수용, 메모리, 현실성, 페르소나                   |
| 4   | [Accounts](static/docs/04-accounts.md)                                   | 게스트, 계정 승계, 리더보드 자격                 |
| 5   | [Evaluation & Observability](static/docs/05-evaluation-observability.md) | 탐지, 검증, 관측                                 |
| 6   | [Operations](static/docs/06-operations.md)                               | 비용, 남용, 데이터 수명, 운영                    |
| 7   | [Architecture & Stack](static/docs/07-architecture-stack.md)             | 스택 근거, 서비스 경계, 커밋 프로토콜, 수용 기준 |

## 🏗️ Services

| 서비스                          | 스택                     | 소유                                                    |
| ------------------------------- | ------------------------ | ------------------------------------------------------- |
| [`ledger/`](ledger/)            | Spring Boot + PostgreSQL | 거래 Commit, Holdings, 사전, 계정, 시장, 리더보드       |
| [`orchestrator/`](orchestrator/) | FastAPI                  | NPC 턴, 페르소나, Provider 추상화, Quiet Window, 분류기 |
| [`web/`](web/)                  | React 19 + Vite + TS     | 웹 클라이언트 — 빌드 산출물을 ledger가 8080에서 서빙    |

테스트: `ledger/`에서 `./gradlew test`(임베디드 PostgreSQL, Docker 불필요),
`orchestrator/`에서 `uv run pytest`. 문서 07의 Acceptance Criteria가 전부 테스트로 존재한다.

도구 설치부터 로컬 직접 실행까지의 상세 절차는 **[실행 가이드](GETTING_STARTED.md)** 참조.

페르소나 데이터 귀속 표기는 [NOTICE.md](NOTICE.md).
