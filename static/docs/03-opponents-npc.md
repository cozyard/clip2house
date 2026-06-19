## Scope

이 문서는 AI 상대가 어떻게 협상하고 사람처럼 반응하는지를 다룬다.  
가치·시장은 게임 문서, 격리·계약은 무결성 문서, 분류·검증은 평가 문서에서 다룬다.

## Persona Records

상대 하나하나는 Persona Record로 구동된다.

- Persona는 문체, 협상 성향, 아이템 선호, Nickname을 결정한다.

- 상대는 Avatar 없이 Nickname으로만 존재한다.  
  중고거래 앱과 같은 결이다.

- 데이터소스는 Nemotron-Personas-Korea다.  
  CC BY 4.0 라이선스의 합성 한국어 Persona이며 귀속 표기가 필요하다.  
  https://huggingface.co/datasets/nvidia/Nemotron-Personas-Korea

- 샘플링은 성별·연령·지역으로 층화 추출하고, 고정 시드로 재현 가능하게 한다.

## Acceptance Function

상대는 매 협상 응답에서 무결성 문서가 정의한 협상 Action 중 하나를 낸다.

- 무엇을 낼지는 수식이 아니라 Persona의 In-Character 판단이다.  
  성향과 Negotiation State, 그리고 서버가 준 가치 비율을 보고 모델이 직접 결정한다.

- 상대는 아이템의 절대 가치를 보지 않는다.  
  대신 서버가 현재 거래의 가치 비율(내주는 것 대비 받는 것)을 동결된 참조 가치로부터 결정적으로 산출해 그대로 준다.

- 구간으로 뭉개지 않는다.  
  비율을 약간·큰으로 묶는 순간 그 경계가 곧 "얼마가 큰가"라는 판단이라 시스템이 Persona 몫을 침범한다.  
  가격에 민감한 Persona가 작은 차이에 반응할 해상도도 사라지기 때문이다.

- 비율이 받아들일 만한지는 Persona 성향(후함·가격 민감도)과 설득이 전부 판단한다.  
  같은 비율도 누구에겐 쯤이야, 누구에겐 이나인 것이 곧 성향이다.  
  주는 것은 절대 가치가 아니라 그 거래 한정의 상대 비율이라, 새더라도 숨긴 가치 체계는 드러나지 않고 협상은 권위 가치에 묶인 채 유지된다.

- 같은 비율도 아이템 Stakes에 따라 무게가 다르다.  
  Persona는 흥정 대상이 클립인지 집인지를 정체성(Flavor)에서 알기에 비율을 그 Stakes로 가중한다.  
  싼 물건의 큰 비율 차이는 흘려보내고 비싼 물건의 작은 차이는 사수한다.  
  바닥에선 Trade Up이 쉽고 꼭대기로 갈수록 어려워지는 난이도 곡선이 Emergent하게 생긴다.

## Negotiation Memory

매 턴 모델에 주는 것은 Persona와 구조화된 Negotiation State, 그리고 짧은 Raw Window다.  
전체 Transcript도 Prose 요약도 아니다.

- Negotiation State는 대상 아이템, 테이블 위 Offer, 오간 제안들, Persona의 입장·기분, 플레이어의 주요 주장을 담는다.

- State는 Typed라 협상 길이와 무관하게 거의 상수 크기다.  
  요약 LLM 호출도, 요약 Drift도 없다.

- 플레이어의 주장을 필드로 들고 있으므로 말 바꾸기를 잡아낼 수 있다.

- 세션 간에는 가벼운 관계 레코드만 재주입한다.  
  지난 거래 결과, Injection 시도 여부, 대략적 관계 상태가 여기 담기며, 과거 대화 전문은 들고 오지 않는다.  
  이것이 Direct Memory다.

## Chat Realism

핵심 가치는 AI이면서 얼마나 AI스럽지 않게 사람처럼 반응하는가다.

- 즉답하지 않는다.  
  User 입력 직후 Persona별 Quiet Window를 두고, User가 계속 입력하면 대기 중인 턴을 취소·재스케줄한다.  
  이는 사람다움인 동시에 Latency를 흡수하고, Window가 끝날 때 한 번만 호출하는 Debounce다.

- 메시지 하나하나에 1대1로 답하지 않는다.  
  최종 작성 시점에 마지막 응답 이후의 User 메시지 전체를 하나의 Input으로 읽는다.  
  응답의 수와 형태는 User가 Burst를 어떻게 쪼갰는지가 아니라 Persona의 문체가 정한다.

- 문체는 하이브리드로 만든다.  
  Persona 속성에서 소수의 문체 파라미터를 명시적으로 뽑아 모델에 제약으로 주고, 실제 문장은 모델이 In-Character로 쓴다.  
  연령대별로 길이·띄어쓰기·구두점·이모티콘·맞춤법이 다르다.

- 읽음 표시는 있고, 상대의 입력 중 표시는 없다.

## Injection Reaction

상대는 Injection 시도에 System-Speak로 답하지 않고 사람의 반응으로 응한다.

- "갑자기 무슨 소리세요", "장난치지 마세요", "차단합니다" 같은 반응이다.

- 이 반응은 분류기 결과와 무관하게 Persona에서 Emergent하다.  
  이상한 입력에는 이상하다는 사람 반응을 하므로 분류기가 놓쳐도 깨지지 않는다.

- 분류기는 별도로 로깅과 Rate Limit 마찰을 줄 뿐 반응을 게이팅하지 않는다.

- 시도는 Silent하게 로깅되며 상대의 발화에는 절대 드러나지 않는다.

## Provider Abstraction

Provider 추상화를 처음부터 둔다.

- Provider 할당은 Persona별 Sticky다.

- Persona의 Provider가 Down이면 그 Persona는 Offline을 Simulate한다.  
  Voice 일관성을 위해 같은 Persona를 다른 Provider로 Failover하지 않는다.

## Reputation

이웃·커뮤니티 단위의 Reputation 전파는 스키마에만 Scaffold하고 이후 Config로 활성화한다.  
초기 동작은 Direct Memory까지다.

## Dependencies

- **Game Design**  
  참조 가치의 절대값은 서버 전용이고 상대에게는 그 거래의 가치 비율만 간다.  
  수용이 어떻게 Trade Up을 만드는지의 규칙은 게임 문서에서 정의된다.

- **Integrity & Concurrency**  
  협상 Action 집합과 Offer·Accept의 Structured Action 계약, User Input 격리는 무결성 문서에서 정의된다.

- **Evaluation & Observability**  
  정책 게이트 분류기, Persona 일관성 검증, 그리고 참조값·Raw Token이 새지 않게 하는 출력 가드는 평가 문서가 맡는다.

- **Operations**  
  Provider 비용, 관계 레코드의 데이터 운영, Rate Limit 마찰은 운영 문서에서 다룬다.

## Open Questions

- **가치 비율의 표현 방식**  
  원비율로 줄지 로그 스케일로 줄지.

- **Quiet Window 길이의 Persona별 분포**  
  Config로 외부화.

- **Reputation 전파 모델**  
  후속 설계.

- **기타**  
  문체 파라미터의 구체 목록과 연령대 매핑.
  관계 레코드의 보존 기간.
