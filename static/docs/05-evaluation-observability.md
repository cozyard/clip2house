## Scope

이 문서는 탐지·검증·관측을 다룬다.  
무엇을 막고, 무엇을 관찰하며, 무엇을 기록하는가다.  
가치·시장은 게임 문서, 격리·경계는 무결성 문서, Persona 행동은 상대 문서에서 다룬다.

## Detection: Policy-Gate Classifier

Injection 탐지는 분류기 한 곳에서만 일어난다.

- 탐지 결과는 세 곳으로 퍼진다.  
  무결성 문서의 Audit(전체 기록), 게임 문서의 Analytics(거래 이벤트의 플래그), 상대 문서의 관계 레코드(세션 간 시도 여부)다.  
  다른 컴포넌트는 Injection을 독립적으로 탐지하지 않으므로 세 곳이 엇갈리지 않는다.

- 분류기는 응답 경로 밖에서 Async로 돈다.  
  Persona 반응은 Emergent라 분류기를 기다리지 않으니 지연이 없고, 분류기가 놓쳐도 무결성과 반응이 깨지지 않는다.

- 튜닝은 Permissive다. 낮은 오탐 쪽으로 둔다.  
  무결성이 분류기에 걸려 있지 않으므로 미탐은 감수할 수 있고, 오탐으로 선량한 플레이어에 마찰을 주는 편이 더 나쁘다.

- 로그는 항상 남기되 마찰은 반복·고조될 때만 건다.  
  단발 의심은 기록만 하고, 지속적 시도에는 Rate Limit 마찰을 건다.

## Output Guards

응답이 플레이어에 닿기 전 인라인으로 검사해 명백한 위반을 막는다.

- 막는 것은 권위 가치와 시스템이 준 비율의 누설, Raw Token, System-Speak다.  
  게임은 물물교환이라 Persona가 값을 부를 일이 거의 없어 게임 속 대사와 충돌이 적다.

- 위반은 못 쓸 출력의 한 종류로 취급한다.  
  무결성 문서의 재시도 경로로 합류해, Bounded 재시도 후에도 실패하면 Persona가 Offline로 Degrade한다.

## Persona Consistency

캐릭터 일관성은 매 메시지를 막지 않고 샘플 기반으로 사후 검증한다.

- 상대 문서가 뽑아 둔 명시 문체 파라미터에 출력을 대조한다.  
  길이·격식·이모티콘율 등이 그 Persona의 선언값에서 벗어나는지를 통계로 본다.

- 미묘한 흔들림은 차단이 아니라 튜닝 신호로 쓴다.  
  명백한 누설과 포맷 붕괴는 Output Guards가 이미 인라인으로 막는다.

## Failure & Availability Monitoring

Degrade와 실패를 추적한다.

- 무결성 문서의 재시도 상한 소진과 상대 문서의 Provider Down은 둘 다 Persona Offline로 나타난다.

- 지속되는 실패율과 Offline 비율을 알림 임계로 감시한다.  
  비용 증폭과 품질 저하의 조기 신호다.

## Analytics Events

게임 상태와 분리된 텔레메트리를 Emit한다.

- 핵심 이벤트는 거래 성사, Offer·Accept·Decline, Hijack, Offline Degrade, Clear 도달, 분류기 Hit다.

- 거래 이벤트에는 거래별 손익과 Injection 플래그가 실린다.

- 이벤트는 플레이어 식별자로 키잉된다.

## Value & Dictionary Health

생성 가치의 품질과 캐시 효율을 관측한다.

- 생성 가치는 버전 코호트 단위로 Golden Anchor에 대조해 체계적 Drift를 감시한다.  
  특정 코호트가 틀어지면 게임 문서의 Provenance로 필터·재생성한다.

- 사전 적중률을 추적한다.  
  신규 생성 비율이 곧 LLM 비용이다.

## Dependencies

- **Game Design**  
  참조 가치·Provenance·Analytics 이벤트의 의미는 게임 문서에서 정의된다.

- **Integrity & Concurrency**  
  Audit 적재와 재시도→Offline 경로, 경계는 무결성 문서가 소유한다.

- **Opponents (NPC)**  
  대조에 쓰는 문체 파라미터, Injection 반응의 Emergent 성격, 관계 레코드는 상대 문서에서 정의된다.

- **Accounts**  
  이벤트·관계 레코드를 키잉하는 플레이어 식별자는 계정 문서에서 정의된다.

- **Operations**  
  비용 지표·로그 보존·파티셔닝은 운영 문서에서 다룬다.

## Open Questions

- 분류기의 오탐·미탐 목표치와 마찰 임계.

- Persona Consistency 샘플링 비율.

- Drift 판정 기준과 코호트 단위.

- Analytics 이벤트의 구체 필드 스키마.
