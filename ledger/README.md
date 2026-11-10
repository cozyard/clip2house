# clip2house Ledger (권위 장부 서버)

Spring Boot 3 + PostgreSQL. 거래 Commit, Holdings, 사전(Dictionary), 계정, 시장, 리더보드를 소유한다.
Model Output과 플레이어 입력은 전부 "제안"이고, 권위 상태의 기록은 이 서버만 한다.
설계 근거는 [static/docs](../static/docs) 문서 7종에 있다.

## 실행

```bash
# PostgreSQL 필요 (기본: localhost:5432/clip2house, clip2house/clip2house)
./gradlew bootRun
```

전체 스택 실행은 [실행 가이드](../GETTING_STARTED.md)를 따른다.
웹 클라이언트 소스는 [`web/`](../web/)(React + Vite)에 있고, `npm run build`가 산출물을
`src/main/resources/static`(git 미추적)에 넣는다. 빌드 후 기동하면 `http://localhost:8080/`에서
바로 플레이할 수 있다(피드·채팅·오퍼 카드·수락·리더보드).

## 테스트

```bash
./gradlew test
```

Docker 없이 임베디드 PostgreSQL(Zonky)로 통합 테스트가 돈다. 문서 07의 Acceptance
Criteria(동시 수락, 멱등, TTL, Faucet, 재순환, 앵커 검증, 사전 동결, 승격, 가치 필드 부재)가
전부 테스트로 존재한다.

## API 개요

공개(`/api`, Bearer 토큰):

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/guests` | 게스트 생성(가입 없이 시작, 클립 지급) |
| POST | `/api/account/promote` | 게스트 → 계정 승격(식별자 유지) |
| POST | `/api/auth/login` | 로그인 |
| GET | `/api/me` | 내 프로필·인벤토리 |
| GET | `/api/feed` | 공유 매물 피드 |
| POST | `/api/negotiations` | 협상 개설 |
| GET | `/api/negotiations/{id}` | 협상 상세(메시지·Offer 카드·Version) |
| POST | `/api/negotiations/{id}/messages` | 채팅 발화 |
| POST | `/api/negotiations/{id}/offers` | 구조화 Offer(내 Holding 바인딩) |
| POST | `/api/negotiations/{id}/accept` | Accept = Commit. `Idempotency-Key` 헤더 필수 |
| POST | `/api/negotiations/{id}/offers/{offerId}/decline` | 상대 제안 거절 |
| GET | `/api/leaderboard?board=VALUE\|CLEAR_ORDER\|CLEAR_TIME\|CLEAR_TRADES` | 척도별 리더보드 |

내부(`/internal`, `X-Internal-Secret` 헤더, 오케스트레이터 전용):
페르소나 동기화, 턴 컨텍스트(가치 비율 포함 — 절대 가치는 절대 안 나감), 페르소나 액션,
Injection 감사, 운영자 교정·코호트 무효화.

## 설정

게임 파라미터는 전부 `application.yml`의 `clip2house.*`에 있다(목표 매물 수 60, TTL 12h,
재순환 p 0.15, 클리어 임계 3억, Gross 판정 log10 거리 2.0, 멱등 키 48h, 시즌 8주 등).
운영 배포에서는 `INTERNAL_API_SECRET` 환경변수를 반드시 덮어라.

## 관측

- `/actuator/prometheus` — Micrometer 지표(활성 매물 게이지, 사전 적중/미스 카운터 등)
- `analytics_events` / `injection_audit` — 월 파티셔닝, 보존 기간 후 보관 후 제거
- `operator_reports` — 주간 운영 리포트(비용·실패·남용·가치 Drift)
