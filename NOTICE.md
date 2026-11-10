# Third-Party Data Attribution

## Nemotron-Personas-Korea

- 원본: [Nemotron-Personas-Korea](https://huggingface.co/datasets/nvidia/Nemotron-Personas-Korea)
- 원저작자: NVIDIA Corporation
- 라이선스: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- 사용 파일: [personas_kr.json](orchestrator/data/personas_kr.json)
- 추출 스크립트: [sample_personas.py](orchestrator/scripts/sample_personas.py)
- 추출 기준: 성별·연령대 층화, 고정 시드

원본 필드는 다음과 같이 사용한다.

| 원본 | 사용 필드 | 처리 |
|---|---|---|
| `persona` | `summary` | 유지 |
| `sex` | `gender` | M/F로 변환 |
| `age` | `age_band` | 연령대로 변환 |
| `province` | `region` | 누락 시 `미상` |
| `uuid` | `source_uuid` | 유지 |

- 추가 항목: 닉네임·협상 성향·문체
- 생성 기준: 원본 UUID·나이 기반의 결정적 생성
