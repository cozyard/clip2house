## Scope

이 문서는 기술스택 선정 근거, 서비스 경계, 데이터 계층, 그리고 선행 프로토타입에서 검증된 커밋 프로토콜과 수용 기준을 다룬다.  
게임 규칙·무결성·상대·계정·평가·운영의 요구는 각 문서가 소유하며, 이 문서는 그 요구를 무엇으로 구현하는가만 답한다.

## Stack Decision

스택은 취향이 아니라 요구에서 도출한다.  
각 문서가 스택에 부과하는 요구는 다음과 같다.

```
무결성 영역
- Version 기반 낙관적 동시성과 이중 확정 0
- 고정 Schema Structured Output 강제

상대 영역
- Provider 추상화와 Degrade
- Quiet Window Debounce

운영 영역
- 시간 파티셔닝·보존·Archival
- 지표 관측과 리포트
- IP·식별자 단위 Rate Limit
```

- **권위 장부 서버는 Spring Boot + PostgreSQL이다.**  
  거래 Commit, Holdings, 사전, 계정, 시장, 리더보드가 여기 속한다.  
  요구의 핵심이 ACID 트랜잭션, 낙관적 락, 파티셔닝, 마이그레이션 규율이다.  
  이는 JVM·Spring 생태계(JPA @Version, Flyway, Micrometer)가 가장 성숙하게 감당한다.

- **LLM 오케스트레이션은 FastAPI다.**  
  NPC 턴, Persona 연기, Provider 추상화, Quiet Window, Injection 분류기가 여기 속한다.  
  Structured Output 강제와 LLM SDK 인체공학은 Python(Pydantic)이 가장 좋고, 비결정적 워크로드를 장부에서 물리적으로 격리한다.

- **프레임워크가 둘인 것은 유행이 아니라 위협 모델의 산물이다.**  
  무결성 문서의 Closed Surface와 Open Surface 구분을 그대로 서비스 경계로 쓴다.  
  Model Output이 장부에 닿을 구조적 수단이 없다는 보증이 프로세스 격리로 한 겹 더 강해진다.

- **본 선정은 TypeScript 프로토타입으로 커밋 프로토콜과 수용 기준을 먼저 검증한 뒤 이루어졌다.**  
  프로토타입의 스택 무관 자산(스키마 설계, 커밋 프로토콜, 테스트 시나리오)은 이 문서로 승계하고 코드는 폐기했다.

## Service Boundary

경계의 계약은 하나다: Model Output과 플레이어 입력은 모두 제안이고, 권위 상태의 기록은 장부 서버만 한다.

- 오케스트레이터는 장부 서버의 내부 API로만 상태 변경을 제안한다.  
  Bot의 Offer·Accept도 이 경로를 지나며, 장부 서버가 Version과 Idempotency를 검증하고 직접 Commit한다.

- 장부 서버는 오케스트레이터에 절대 가치를 주지 않는다.  
  동결된 참조 가치로부터 결정적으로 산출한 거래의 가치 비율만 넘긴다.

- 장애는 경계에서 격리된다.  
  오케스트레이터나 Provider가 죽으면 해당 Persona가 Offline을 Simulate할 뿐, 장부·피드·리더보드는 계속 돈다.  
  무결성 문서의 Availability Under Attack이 아키텍처로 구현된다.

## Data Layer

- 내부 식별자는 BIGINT 순차 키다.  
  Holding 소유·리더보드·관계 레코드가 전부 이것을 가리키고, 외부에는 불투명 토큰과 해시 핸들만 나간다.

- 경합 자원(Listing, Holding)은 Version 컬럼을 가진다.  
  Commit은 기대 Version을 조건으로 하는 조건부 UPDATE이며, 영향 행 0이면 그 거래는 무효다.

- Idempotency 키는 키·결과·만료를 저장하는 전용 테이블이다.  
  같은 키의 재도착은 재실행 없이 저장된 결과를 반환한다.

- Audit·Analytics는 시각 기준 파티셔닝으로 적재한다.  
  보존 기간 후 Archival한다.

- 사전은 Write-Once이며 Provenance 컬럼(생성 버전, 모델, 프롬프트, 시각, 검증 상태)을 보유한다.  
  운영자 교정은 원값을 덮지 않고 교정값 컬럼에 쓴다.

- 스키마 변경은 Flyway 마이그레이션으로만 한다.

## Commit Protocol

프로토타입에서 검증된 흐름이다.

1. Accept는 특정 Offer에 바인딩되며 기대 Listing Version, 기대 Holding Version, Idempotency-Key를 싣는다.
2. 서버는 키를 먼저 조회해 기존 결과가 있으면 그대로 반환한다.
3. 단일 트랜잭션 안에서 Offer가 Standing인지, 협상이 Active인지, Version이 일치하는지 검증한다.
4. 통과 시 Listing 소비, 내준 Holding 폐기, 새 Holding 부여, 거래 기록, 같은 매물 위의 다른 협상 무효화를 한 번에 Commit한다.
5. 어긋나면 그 거래만 무효가 되고, 플레이어에게는 "이미 거래됨"(Hijacking)으로 드러난다.
6. 결과는 성공·실패 모두 Idempotency 키에 저장한다.

## Acceptance Criteria

구현이 스택과 무관하게 통과해야 하는 시나리오다.  
프로토타입에서 전부 테스트로 검증되었다.

- 같은 매물을 두 플레이어가 동시 수락하면 첫 Commit만 성공하고, 패자의 협상은 무효화되며 Hijack 이벤트가 남는다.
- 같은 Holding을 두 협상에 동시에 내밀면 첫 Commit만 성공한다.
- 기대 Version이 어긋난 Accept는 무효다.
- 같은 Idempotency 키의 재요청은 거래를 늘리지 않고 동일 결과를 반환한다.
- 매물 TTL 만료는 그 위의 협상을 무효화한다.
- Faucet은 목표 Active 매물 수까지만 생성하고, 도달 후에는 발화하지 않는다.
- 내준 물건은 확률 p로만 재매물이 되며 재순환 매물도 TTL을 가진다.
- 자릿수가 어긋난 가치 제안(클립이 집값)은 Golden Anchor 대조로 거부된다.
- 사전에 있는 아이템은 재계산 없이 재사용되고, 뒤늦은 다른 제안값은 동결값을 바꾸지 못한다.
- 불량 생성 버전은 코호트 단위로 무효화되며 앵커는 대상이 아니다.
- LLM 재시도 상한 소진 시 Persona만 Offline이 되고 협상·서비스는 살아 있다.
- 가치·비율·System-Speak가 섞인 출력은 플레이어에게 닿지 않고 재시도 경로로 합류한다.
- 게스트 승격 후에도 내부 식별자·인벤토리·랭킹이 그대로다.
- 피드·리더보드 등 어떤 응답에도 참조 가치 필드가 존재하지 않는다.

## Initial Parameters

각 문서 Open Questions의 초기값 제안이다.  
전부 코드가 아니라 외부 Config에 둔다.

- 목표 Active 매물 수 60, 매물 TTL 12시간, 재순환 확률 p 0.15.
- 공급 분포는 log10 가치 2~9의 넓은 균등 분포.
- 클리어 임계 3억 원.
- Golden Anchor는 클립 100원부터 아파트 8억 원까지 가치 범위를 log 스케일로 덮는 11종.
- Gross 오류 판정은 인용 앵커와의 log10 거리 2.0 초과.
- LLM 재시도 상한 2회, Offline Degrade 10분.
- Quiet Window는 Persona별 8~90초 분포.
- 게스트 토큰 TTL 30일, Idempotency 키 보존 48시간.
- 시즌 길이 8주.

## Dependencies

- **Game Design**  
  가치·시장·클리어 규칙은 게임 문서가 정의하고, 이 문서는 그 저장·계산 위치만 정한다.

- **Integrity & Concurrency**  
  서비스 경계와 커밋 프로토콜은 무결성 문서의 Surface 구분과 계약을 구현한 것이다.

- **Opponents (NPC)**  
  오케스트레이터가 소유하는 턴·메모리·Provider 규칙은 상대 문서가 정의한다.

- **Evaluation & Observability / Operations**  
  파티셔닝·보존·지표의 의미는 해당 문서가 소유하며, 이 문서는 구현 수단(Micrometer, 파티션 테이블)만 정한다.

## Open Questions

- **서비스 간 통신**  
  동기 REST로 시작할지, 턴 스케줄을 큐로 뺄지. 다중 인스턴스 전에는 결정이 필요하다.

- **Quiet Window 스케줄러의 외부화**  
  인프로세스 타이머는 단일 인스턴스 한정이다. DB 지연 큐 또는 외부 큐 중 택일.

- **Rate Limit 공유 스토어**  
  다중 인스턴스 시 Redis 도입 여부와 시점.

- **인증 방식**  
  계정 문서는 방식을 구현 선택으로 열어 두었다. 세션·JWT 중 택일.

- **배포 형상**  
  두 서비스의 컨테이너 구성과 관측 스택 연결.
