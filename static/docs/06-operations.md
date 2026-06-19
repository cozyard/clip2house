## Scope

이 문서는 운영을 다룬다.  
비용, 남용 제어, 데이터 수명, 운영자 가시성이다.

## Cost

LLM 호출이 주 비용이다.

- LLM 비용은 활성 협상과 진짜 신규 아이템 생성에만 든다.  
  사전 리필은 캐시에서 Instantiate하는 Cache-Cheap 작업이라 비용이 없고, 협상 비용은 활성 플레이어 수에 비례한다.

- Quiet Window의 Debounce가 Burst를 한 번의 호출로 묶어 Turn당 호출을 줄인다.

- 재시도 상한이 비용 증폭 가드를 겸한다.  
  못 쓸 출력에 무한정 재호출하지 않고 Offline로 Degrade한다.

- Provider 추상화로 모델과 Provider를 비용에 따라 갈아끼울 수 있다.

## Rate Limit & Abuse

호출과 토큰 양산을 제어한다.

- 게스트 토큰 양산은 Rate Limit으로 막는다.  
  가치는 벌어야 올라 순위 남용은 안 되지만, 토큰 양산과 그에 딸린 호출 비용은 제어한다.

- Injection 시도는 분류기가 기록하고 반복·고조 시 Rate Limit 마찰을 건다.

- 호출 한도는 플레이어 식별자와 IP 단위로 둔다.

## Data Retention & Partitioning

행은 사전과 무관하게 자란다.

- Holdings와 매물 테이블은 만료·Archival이 필요하다.  
  게임 문서의 Sink·TTL이 활성 행을 묶지만, 이력은 별도 보존 정책을 둔다.

- Audit와 Analytics는 시간 파티셔닝으로 적재하고 보존 기간 후 Archival한다.

- 사전은 Write-Once라 자라지만 재사용으로 가치 계산 비용을 줄인다.  
  Provenance를 키로 코호트 단위 관리가 가능하다.

## Provenance Operations

생성 가치는 모두 Provenance를 갖는다.  
필드 구성은 게임 문서가 정의한다.

- 불량 코호트는 Provenance로 필터·재생성하고, 운영자 교정 경로를 둔다.

## Operator Report

주간 단위로 운영 상태를 요약한다.

- 비용: 호출 수, 신규 생성 비율, 사전 적중률.

- 실패: Offline 비율, 재시도 소진.

- 남용: Injection 빈도, Rate Limit 발동.

- 건전성: 가치 Drift, 코호트 경고.

## Dependencies

- **Game Design**  
  Sink·TTL·Provenance·사전은 게임 문서에서 정의된다.

- **Integrity & Concurrency**  
  Audit 적재와 재시도 → Offline은 무결성 문서가 소유한다.

- **Opponents (NPC)**  
  Provider 추상화와 Quiet Window는 상대 문서에서 정의된다.

- **Evaluation & Observability**  
  비용·실패·남용·Drift 지표는 평가 문서가 산출한다.

- **Accounts**  
  게스트 토큰·식별자 단위 Rate Limit은 계정 문서와 공유한다.

## Open Questions

- 각 테이블의 보존 기간과 Archival 주기.

- Rate Limit 구체 한도.

- 운영자 리포트의 전달 형식과 주기 세부.
