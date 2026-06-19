## Scope

이 문서는 무엇이 Model Output에 의해 오염될 수 없는지와, 경합 상황에서의 거래 정합성을 정의한다.  
게임 규칙·가치는 게임 문서, 분류·모니터링은 평가 문서, 비용·로그 운영은 운영 문서에서 다룬다.

## Threat Model

막는 것은 두 가지다.  
Injection이 서비스를 멈추거나 권위 게임 상태를 오염시키려는 시도, 그리고 경합으로 인한 거래의 이중 확정·유실이다.

막지 않는 것도 명시한다.  
설득으로 협상 결과를 움직이는 것은 위협이 아니라 게임플레이다.  
Injection은 허용된 전략이며, 우리는 그 효과가 아니라 그것이 닿을 수 있는 Surface만 통제한다.

## The Boundary

경계는 Injection의 종류가 아니라 Model Output이 무엇에 닿을 수 있는가로 긋는다.  
한 번의 시도가 둘 다 노릴 수 있으므로 입력을 분류해 가르지 않고, 출력이 쓸 수 있는 Surface를 가른다.

- Closed Surface는 서버가 소유하는 구조·정체성·장부다.  
  Schema 적합성, Action Enum, 아이템 정체성, 동결된 참조 가치, Holdings, 거래 Commit, 리더보드 순위가 여기 속한다.  
  Model Output은 이 Surface에 쓸 구조적 수단 자체가 없다.

- Open Surface는 설득으로 움직이는 협상 결과다.  
  상대를 설득해 불리한 거래를 받아내는 것은 오염이 아니라 정당한 게임 산물이다.

무결성은 정책 게이트 분류기에 의존하지 않는다.  
분류기는 오류가 나고 Permissive Fallback이라, 거기 보증을 태우면 분류가 틀리는 순간 뚫린다.  
보증은 분류 성공 여부와 무관하게 Model Output이 협상 채널로만 들어오고 장부엔 닿지 못한다는 구조에서 나온다.  
분류기는 분석·마찰용이지 안전 경계가 아니다.

## Isolation Layers

세 겹으로 격리한다.

- **Structural**  
  User Input은 System Prompt에 절대 Concatenate하지 않고 별도 Context Slot으로 전달한다.

- **Schema Enforcement**  
  응답은 고정 Schema의 Structured Output이며 Raw Text를 Parse하지 않는다.  
  Injection이 임의 형식의 출력을 만들 수 없다.

- **Parse-Time Validation**  
  Action은 고정 Enum으로 검증하고, 범위를 벗어난 값은 게임 로직에 닿기 전에 거부한다.  
  애초에 권위 상태를 기술하는 필드를 Schema에 두지 않으므로, "상태를 바꾸지 않았다"를 단언할 필요조차 없다.

## Authoritative State & Commit

장부는 서버가 소유한다.  
Model Output과 플레이어 입력은 모두 제안일 뿐이고, 권위 상태의 기록은 서버만 한다.

- 거래는 한쪽이 올린 구체적 Offer와 다른 쪽의 Accept로 성립한다.  
  Offer와 Accept는 자유 텍스트가 아니라 특정 품목에 바인딩된 Structured Action이며, 받는 쪽에는 수락·거절 카드로 뜬다.  
  설득은 대화에서 일어나고 Commit은 구조화된 액션에서만 일어난다.  
  Prose에서 합의를 추론하지 않으므로 모호하지도, Injection으로 위조되지도 않는다.

- Accept는 의사 표시일 뿐이다.  
  서버가 그 Accept를 받아 Version과 Idempotency를 검증하고 직접 Commit한다.  
  Bot의 Accept도 테이블 위 구체적 플레이어 Offer를 받을 때만 Commit이며, 단순한 거래 의향은 Offer가 아니다.

- 별도 확인 단계 없이 Accept 시 Commit한다.  
  손해 거래도 막지 않는 설계이므로 실수로 확정된 불리한 거래도 의도된 결과다.

## Optimistic Concurrency

공유 자원의 경합은 Version 기반 Optimistic Concurrency로 해소한다.

- Version은 Listing과 플레이어 Holding에 둔다.  
  Listing은 모두가 경쟁하는 공유 자원이고, Holding은 같은 물건이 두 거래에 동시에 나가는 것을 막는다.  
  여러 상대에게 Offer를 동시에 띄워두고 한꺼번에 성사시키려 해도, Holding Version이 어긋나 첫 Commit만 성공하고 나머지는 무효가 된다.  
  Chat Room에는 Version이 필요 없다.  
  메시지 순서는 Turn 모델이 처리하며 거래 무결성과 무관하다.

- Accept는 기대 Version을 실어 보낸다.  
  Commit 시점에 Version이 어긋나면 그 거래는 무효가 된다.

- 충돌은 Silent Retry가 아니라 게임 이벤트로 드러난다.  
  Listing이 먼저 소비됐으면 "이미 거래됨"으로 협상이 무효가 되며, 이것이 플레이어가 겪는 Hijacking이다.  
  여기서 충돌은 인프라 글리치가 아니라 세상이 바뀌었다는 의미 있는 피드백이다.

## Idempotency

상태를 바꾸는 쓰기 요청은 Idempotency-Key를 수용한다.

- 키는 논리적 액션 단위로 발급한다.  
  Accept 한 번에 키 하나이며, 네트워크 재시도는 같은 키를 재사용한다.

- 서버는 키와 결과를 저장한다.  
  같은 키가 다시 오면 재실행 없이 저장된 결과를 반환하므로, 거래가 중복 확정되지 않는다.

- 키는 재시도 창을 덮을 만큼만 보존하고 TTL로 정리한다.

## Availability Under Attack

Model Output을 못 쓰는 경우에도 서비스는 멈추지 않는다.

- 깨진 Schema나 호출 실패는 한 Turn에 한해 제한된 횟수만 재시도한다.  
  재시도 상한은 Config로 빼 튜닝한다.

- 상한을 넘으면 그 Persona는 응답하지 않고 잠시 Offline으로 Degrade한다.  
  서비스 전체는 계속 돌고 다음 Turn에 다시 시도한다.

- 이 상한은 Cost Amplification 방어도 겸한다.  
  출력을 안정적으로 깨는 Injection이 매 메시지마다 무한 호출을 유발하지 못한다.

- 지속적인 실패율 급증은 재시도로 보완할 문제가 아니라 생성 단계의 문제다.  
  이는 평가·운영 문서의 모니터링이 드러낸다.

## Audit

Injection 시도는 Silent하게 로깅한다.  
시도의 Raw Input, 분류된 타입, Bot 응답을 별도 테이블에 적재한다.  
이 로그는 Bot의 발화에 절대 드러나지 않는다.

## Guarantees

- 게임 상태 오염 = 0.  
  어떤 Injection 시도에도 권위 상태는 Model Output에 의해 바뀌지 않는다.

- 거래의 이중 확정 = 0.  
  동시 경합 상황에서도 한 거래는 한 번만 확정된다.

- 리더보드 순위는 플레이어가 직접 조작할 수 없다.  
  순위가 파생되는 참조 가치를 서버가 소유하고 동결하기 때문이다.

## Dependencies

- **Game Design**  
  참조 가치의 정확성 검증과 동결 정책은 게임 문서에서 정의된다.  
  이 문서는 동결된 값이 변조되지 않음만 보증한다.

- **Opponents (NPC)**  
  Bot이 언제 Offer·Accept를 내는지의 수용 함수와 Injection에 대한 사람다운 반응은 상대 문서에서 정의된다.

- **Evaluation & Observability**  
  Injection 분류와 재시도·실패율 모니터링은 평가 문서가 맡는다.  
  무결성은 분류 결과에 의존하지 않는다.

- **Operations**  
  Idempotency 키와 Audit 로그의 보존·비용은 운영 문서에서 다룬다.

- **Accounts**  
  게스트·계정 상태의 권위와 지속성은 Accounts 문서에서 다룬다.  
  이 문서는 서버가 권위 상태를 소유한다는 원칙만 일반화한다.

## Open Questions

- **Action Enum의 구체 멤버 구성**  
  Continue, Offer, Accept, Decline.  
  Counter는 Standing Offer가 있을 때의 Offer라 별도 멤버가 아니다.

- **재시도 상한과 Idempotency 키 보존 기간의 구체값**  
  Config로 외부화.

- **Audit 테이블의 파티셔닝·보존 정책**  
  운영 문서와 공유.
