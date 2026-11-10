# 실행 가이드

## 구성

| 서비스 | 포트 | 역할 |
|---|---|---|
| PostgreSQL 16 | 5432 | 계정·물건·거래 기록 |
| Ledger | 8080 | 게임 API와 웹 화면 |
| Orchestrator | 8100 | NVIDIA API를 통한 상대 응답 |
| Web 개발 서버 | 5173 | 화면 개발과 미리보기 |

## NVIDIA 설정

1. `orchestrator/.env.example`을 `orchestrator/.env`로 복사한다.
2. [NVIDIA API Catalog](https://build.nvidia.com/nvidia/nemotron-3.5-lightning-30b-a3b)에서 발급한 키를 `C2H_NVIDIA_API_KEY`에 입력한다.
3. 아래 Docker 또는 로컬 실행 절차를 따른다.

기본 제공자는 `nvidia`이고 모델은 `nvidia/nemotron-3.5-lightning-30b-a3b`이다. 키는 서버에서만 읽으며 `.env`는 Git과 Docker 빌드에서 제외한다. 설정 항목은 [환경변수 예시](orchestrator/.env.example)에서 관리한다.

## Docker 실행

Docker Desktop을 실행한 뒤 저장소 루트에서 실행한다.

```sh
docker compose --env-file orchestrator/.env up --build
```

[게임 화면](http://localhost:8080)에서 시작한다. 키 없이 기능을 확인할 때는 `.env`의 `C2H_PROVIDER`를 `mock`으로 바꾼다. `mock`은 정해진 규칙으로 응답한다.

## 로컬 실행

Java 21, Node.js 22.12 이상, Python 3.12 이상, uv, PostgreSQL 16을 사용한다. Windows에서는 다음과 같이 설치한다.

```powershell
winget install --id EclipseAdoptium.Temurin.21.JDK
winget install --id OpenJS.NodeJS.LTS
winget install --id astral-sh.uv
winget install --id PostgreSQL.PostgreSQL.16
```

설치 후 새 터미널을 열고 PostgreSQL 계정과 데이터베이스를 만든다. 스키마는 장부 서버가 처음 실행될 때 생성한다.

```powershell
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -c "CREATE USER clip2house WITH PASSWORD 'clip2house';"
& "C:\Program Files\PostgreSQL\16\bin\psql.exe" -U postgres -c "CREATE DATABASE clip2house OWNER clip2house;"
```

각 서비스는 별도 터미널에서 실행한다.

| 순서 | 위치 | 명령 |
|---|---|---|
| 1 | `web/` | `npm ci`, `npm run build` |
| 2 | `ledger/` | Windows: `.\gradlew.bat bootRun`, macOS/Linux: `./gradlew bootRun` |
| 3 | `orchestrator/` | `uv sync`, `uv run uvicorn app.main:app --port 8100` |

[게임 화면](http://localhost:8080)에서 매물을 고르고 채팅으로 교환을 제안한다. 화면 개발 중에는 `web/`에서 `npm run dev`를 실행하고 [개발 화면](http://localhost:5173)을 사용한다.

## 검증

| 위치 | 명령 | 범위 |
|---|---|---|
| `web/` | `npm run build` | 타입 검사와 배포 빌드 |
| `ledger/` | `./gradlew test` | 임베디드 PostgreSQL 통합 테스트 |
| `orchestrator/` | `uv run pytest` | 응답 처리·분류·스케줄링·제공자 오류 처리 |

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다. 장부 테스트에는 별도 데이터베이스가 필요하지 않다. 테스트는 외부 LLM을 호출하지 않는다.

## 장부 설정

기본값은 `ledger/src/main/resources/application.yml`에서 관리한다.

| 환경변수 | 용도 |
|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL 주소 |
| `SPRING_DATASOURCE_USERNAME` | 데이터베이스 사용자 |
| `SPRING_DATASOURCE_PASSWORD` | 데이터베이스 비밀번호 |
| `INTERNAL_API_SECRET` | 장부와 오케스트레이터의 내부 인증 비밀 |

배포 시 개발용 비밀번호와 내부 인증 비밀을 교체한다. 비공유 보관 자료는 `.local/archive/`, 임시 작업은 `.local/tmp/`에 둔다. 파티션 보관 자료는 장부 실행 경로의 `.local/archive/partitions/`에 저장한다.

## 문제 해결

| 증상 | 확인할 사항 |
|---|---|
| 피드에 매물이 없음 | 오케스트레이터의 페르소나 동기화와 장부의 공급 작업을 확인한다. |
| 상대가 응답하지 않음 | NVIDIA 키·모델 접근 권한·호출 한도와 오케스트레이터 로그를 확인한다. |
| NVIDIA 키 누락 오류 | `orchestrator/.env`에 키를 입력한다. Docker는 `--env-file orchestrator/.env`를 지정한다. |
| 장부가 실행되지 않음 | Java 21과 PostgreSQL 연결을 확인한다. |
| 8080에서 빈 화면 또는 404 | `web/`에서 `npm run build`를 실행한다. |
| 이미 거래된 매물 | 다른 플레이어가 먼저 교환한 매물이다. 피드에서 다른 매물을 고른다. |

운영 지표와 보존 정책은 [운영 문서](static/docs/06-operations.md)를 참조한다.
