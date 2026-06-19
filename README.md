<div align="center">
  <img src="./static/image/c2h.png" alt="One Clip To House" width="50%">
</div>

# 📝 Overview

빨간 클립 하나로 시작하는 물물교환 시뮬레이션 게임이다.  
상대는 전부 LLM이 연기하는 사람들이고, 채팅으로 흥정해 거래한다.

# 🧠 Design Philosophy

### 제약을 현실로 —

```
LLM의 기술적 제약을 결함이 아니라 현실성으로 치환한다.

Latency → 사람은 바로 답장하지 않는다.  
Quiet Window, Batching → 사람은 밀린 메시지를 몰아 읽고 한 번에 답한다.  
Rate Limit, Provider Down → 사람은 자리를 비우거나, 의도적으로 피할 수 있다.
```

### 무결성은 구조로, 설득은 게임으로 —

```
지켜야 할 대상은 서버 소유 상태와 구조화된 거래다.  
프롬프트 인젝션은 막아야 할 위협이 아니라 핵심 플레이다.
```

### 시스템은 사실을, 페르소나는 판단을 —

```
시스템은 동결된 가치와 거래 비율 같은 사실을 쥐고, 페르소나는 그걸 받아들일지 판단한다.  
```

# 🗂️ Documentation

| # | 문서 | 내용 |
|---|------|------|
| 1 | [Game Design](static/docs/01-game-design.md) | 규칙, 가치, 시장, 진행, 리더보드 |
| 2 | [Integrity & Concurrency](static/docs/02-integrity-concurrency.md) | 오염 불가 경계와 경합 정합성 |
| 3 | [Opponents (NPC)](static/docs/03-opponents-npc.md) | 수용, 메모리, 현실성, 페르소나 |
| 4 | [Accounts](static/docs/04-accounts.md) | 게스트, 계정 승계, 리더보드 자격 |
| 5 | [Evaluation & Observability](static/docs/05-evaluation-observability.md) | 탐지, 검증, 관측 |
| 6 | [Operations](static/docs/06-operations.md) | 비용, 남용, 데이터 수명, 운영 |
