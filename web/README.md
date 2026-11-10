# clip2house Web (웹 클라이언트)

React 19 + Vite + TypeScript + TanStack Query + React Router + Tailwind CSS v4.
서버가 권위다 — 이 클라이언트는 표시와 입력 전달만 하고, 판단(버전 검증·커밋)은 전부 장부 서버가 한다.

## 개발

```bash
npm install
npm run dev      # http://localhost:5173 — /api는 장부 서버(8080)로 프록시
```

## 빌드

```bash
npm run build    # tsc 타입체크 후 ../ledger/src/main/resources/static 으로 산출
```

산출물은 git에 올라가지 않는다. 장부 서버를 소스로 돌릴 때는 먼저 빌드해야
`http://localhost:8080`에서 클라이언트가 뜬다. Docker 빌드(`ledger/Dockerfile`)는
이미지 안에서 web/을 함께 빌드하므로 별도 절차가 없다.

## 구조

```
src/
  api/       fetch 래퍼·토큰 저장소, API 타입, TanStack Query 훅
  hooks/     협상 SSE 구독(신호 수신 → REST 재조회, 실패 시 폴링 폴백)
  screens/   Start(게스트 시작·로그인) / Main(피드·내 물건·랭킹) / Chat(협상)
  components/ 아이템 카드, 스켈레톤·빈 상태, 토스트
  lib/       이모지 썸네일 규칙, 시간 표기, 공용 컨트롤 클래스
  styles/    Tailwind 테마 토큰(디자인 시스템의 시작점)
```

- 라우팅: `/`(메인), `/negotiations/:id`(협상 채팅). 새로고침은 서버의
  SPA 포워딩(`SpaForwardingConfig`)이 index.html로 되돌려 처리한다.
- 채팅은 바닥 근처에 있을 때만 자동 스크롤한다 — 위로 올려 읽는 중엔 위치를 지킨다.
- 폰트는 전부 번들에 셀프호스팅한다(외부 CDN 없음): 본문 Pretendard, 이모지 **Noto Color Emoji**.
  아이템 썸네일·내비 아이콘이 이모지라 OS 기본에 맡기면 플랫폼마다 게임 비주얼이 달라진다
  (Windows 11은 📎를 클리피로 그린다). Noto로 전 플랫폼 동일 렌더링을 강제한다.
  - 주의: fontsource의 noto-color-emoji 패키지는 Chromium이 렌더링 못 하는 포맷이라
    `src/styles/noto-color-emoji/`에 구글 공식 woff2(OFL, LICENSE.txt 동봉)를 직접 둔다.
  - 브랜드 클립은 서사상 "빨간" 클립이다 — Noto의 파란 클립을 `.red-clip` 필터로 재채색한다.

## 디자인 언어

게임임을 드러내는 듀오링고식 문법을 쓰되, 브랜드 색은 빨간 클립의 주황을 유지한다.

- **3D 눌림 버튼**: `box-shadow: 0 3px 0 <진한색>` + `active:translate-y-[3px]` —
  바닥 엣지가 그림자라 눌러도 주변 레이아웃이 안 흔들린다(`lib/ui.ts`).
- **2px 보더**: 그림자 대신 보더로 구분한다. 카드·입력·말풍선 공통.
- **역할 색**: 주황 = 브랜드·주행동, 초록(`success`) = 수락/성사, 파랑(`info`) = 필터·정보,
  금색(`gold`) = 랭킹·클리어. 토큰은 `styles/global.css`의 `@theme`에 있다.
- 하단 내비게이션(이모지 아이콘)이 주 이동 수단이다 — 상단 탭 아님.
