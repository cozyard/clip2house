-- 내부 식별자는 전부 BIGINT 순차 키. 외부에는 불투명 토큰과 닉네임만 나간다. (07-architecture-stack.md, Data Layer)

CREATE TABLE players (
    id           BIGSERIAL PRIMARY KEY,
    nickname     VARCHAR(40)  NOT NULL,
    email        VARCHAR(255) UNIQUE,
    password_hash VARCHAR(100),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    promoted_at  TIMESTAMPTZ
);

-- 클라이언트가 드는 것은 추측 불가 토큰이고, 서버는 해시만 저장한다. 게스트 승격 후에도 player_id는 그대로다.
CREATE TABLE access_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    player_id  BIGINT      NOT NULL REFERENCES players (id),
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('GUEST', 'ACCOUNT')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_tokens_player ON access_tokens (player_id);

-- 사람이 검수한 불변 앵커. 생성값 검증의 유일한 기준이며 코호트 무효화의 대상이 아니다.
CREATE TABLE golden_anchors (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(80)    NOT NULL UNIQUE,
    ref_value NUMERIC(18, 0) NOT NULL
);

-- 사전은 Write-Once. 운영자 교정은 원값(ref_value)을 덮지 않고 corrected_value에 쓴다.
CREATE TABLE items (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120)   NOT NULL,
    name_key          VARCHAR(120)   NOT NULL UNIQUE,
    description       VARCHAR(500)   NOT NULL,
    ref_value         NUMERIC(18, 0) NOT NULL,
    corrected_value   NUMERIC(18, 0),
    gen_version       VARCHAR(40)    NOT NULL,
    gen_model         VARCHAR(80)    NOT NULL,
    gen_prompt_id     VARCHAR(80)    NOT NULL,
    cited_anchor_id   BIGINT         NOT NULL REFERENCES golden_anchors (id),
    validation_status VARCHAR(20)    NOT NULL DEFAULT 'VALID'
        CHECK (validation_status IN ('VALID', 'COHORT_INVALIDATED')),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_items_gen_version ON items (gen_version);

-- 오케스트레이터가 기동 시 동기화하는 페르소나 레지스트리. 장부는 닉네임 표시용 최소 정보만 든다.
-- community는 Reputation 전파 Scaffold이며 초기에는 사용하지 않는다. (03-opponents-npc.md, Reputation)
CREATE TABLE personas (
    id        BIGINT PRIMARY KEY,
    nickname  VARCHAR(40) NOT NULL,
    community VARCHAR(60),
    active    BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE listings (
    id         BIGSERIAL PRIMARY KEY,
    item_id    BIGINT      NOT NULL REFERENCES items (id),
    persona_id BIGINT      NOT NULL REFERENCES personas (id),
    origin     VARCHAR(10) NOT NULL CHECK (origin IN ('FAUCET', 'RECYCLED')),
    status     VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CONSUMED', 'EXPIRED')),
    version    BIGINT      NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_listings_active ON listings (expires_at) WHERE status = 'ACTIVE';

CREATE TABLE holdings (
    id          BIGSERIAL PRIMARY KEY,
    player_id   BIGINT      NOT NULL REFERENCES players (id),
    item_id     BIGINT      NOT NULL REFERENCES items (id),
    status      VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRADED_AWAY')),
    version     BIGINT      NOT NULL DEFAULT 0,
    acquired_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at TIMESTAMPTZ
);
CREATE INDEX idx_holdings_player_active ON holdings (player_id) WHERE status = 'ACTIVE';

CREATE TABLE negotiations (
    id                  BIGSERIAL PRIMARY KEY,
    listing_id          BIGINT      NOT NULL REFERENCES listings (id),
    player_id           BIGINT      NOT NULL REFERENCES players (id),
    status              VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'INVALIDATED')),
    invalidation_reason VARCHAR(20),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at           TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_negotiations_active ON negotiations (listing_id, player_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_negotiations_listing_active ON negotiations (listing_id) WHERE status = 'ACTIVE';

CREATE TABLE chat_messages (
    id             BIGSERIAL PRIMARY KEY,
    negotiation_id BIGINT        NOT NULL REFERENCES negotiations (id),
    sender         VARCHAR(8)    NOT NULL CHECK (sender IN ('PLAYER', 'PERSONA')),
    content        VARCHAR(2000) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    read_at        TIMESTAMPTZ
);
CREATE INDEX idx_chat_messages_negotiation ON chat_messages (negotiation_id, id);

-- Offer는 자유 텍스트가 아니라 플레이어 Holding에 바인딩된 구조화 액션이다. (02-integrity-concurrency.md)
-- 거래는 항상 "플레이어 Holding <-> 매물 아이템" 1대1 교환이라 플레이어 쪽 품목만 지정하면 된다.
CREATE TABLE offers (
    id                BIGSERIAL PRIMARY KEY,
    negotiation_id    BIGINT      NOT NULL REFERENCES negotiations (id),
    proposer          VARCHAR(8)  NOT NULL CHECK (proposer IN ('PLAYER', 'PERSONA')),
    player_holding_id BIGINT      NOT NULL REFERENCES holdings (id),
    status            VARCHAR(12) NOT NULL DEFAULT 'STANDING'
        CHECK (status IN ('STANDING', 'SUPERSEDED', 'ACCEPTED', 'DECLINED', 'INVALIDATED')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at       TIMESTAMPTZ
);
CREATE INDEX idx_offers_negotiation_standing ON offers (negotiation_id) WHERE status = 'STANDING';

CREATE TABLE trades (
    id                  BIGSERIAL PRIMARY KEY,
    negotiation_id      BIGINT      NOT NULL UNIQUE REFERENCES negotiations (id),
    player_id           BIGINT      NOT NULL REFERENCES players (id),
    listing_id          BIGINT      NOT NULL REFERENCES listings (id),
    given_holding_id    BIGINT      NOT NULL REFERENCES holdings (id),
    received_holding_id BIGINT      NOT NULL REFERENCES holdings (id),
    executed_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trades_player ON trades (player_id);

-- 같은 키의 재도착은 재실행 없이 저장된 결과를 반환한다. 성공·실패 모두 저장한다.
CREATE TABLE idempotency_keys (
    key           VARCHAR(120) PRIMARY KEY,
    player_id     BIGINT      NOT NULL,
    http_status   INT         NOT NULL,
    response_body JSONB       NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);

-- Direct Memory: 세션 간 재주입되는 가벼운 관계 레코드. 과거 대화 전문은 들지 않는다. (03-opponents-npc.md)
CREATE TABLE relationships (
    player_id            BIGINT      NOT NULL REFERENCES players (id),
    persona_id           BIGINT      NOT NULL REFERENCES personas (id),
    last_outcome         VARCHAR(20) NOT NULL DEFAULT 'NONE'
        CHECK (last_outcome IN ('NONE', 'TRADED', 'DECLINED', 'HIJACKED', 'EXPIRED')),
    injection_attempted  BOOLEAN     NOT NULL DEFAULT FALSE,
    standing             VARCHAR(12) NOT NULL DEFAULT 'NEUTRAL'
        CHECK (standing IN ('FRIENDLY', 'NEUTRAL', 'WARY')),
    trades_count         INT         NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (player_id, persona_id)
);

CREATE TABLE seasons (
    id        BIGSERIAL PRIMARY KEY,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at   TIMESTAMPTZ NOT NULL
);

-- inventory_value는 서버 전용 파생값이며 어떤 API 응답에도 나가지 않는다.
CREATE TABLE season_players (
    season_id           BIGINT         NOT NULL REFERENCES seasons (id),
    player_id           BIGINT         NOT NULL REFERENCES players (id),
    inventory_value     NUMERIC(18, 0) NOT NULL DEFAULT 0,
    trades_count        INT            NOT NULL DEFAULT 0,
    started_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    cleared_at          TIMESTAMPTZ,
    clear_rank          INT,
    clear_duration_secs BIGINT,
    trades_at_clear     INT,
    PRIMARY KEY (season_id, player_id)
);
CREATE INDEX idx_season_players_value ON season_players (season_id, inventory_value DESC);

-- Audit·Analytics는 시각 기준 월 파티셔닝으로 적재하고 보존 기간 후 Archival한다. (06-operations.md)
CREATE TABLE injection_audit (
    id              BIGSERIAL,
    player_id       BIGINT      NOT NULL,
    persona_id      BIGINT,
    negotiation_id  BIGINT,
    raw_input       TEXT        NOT NULL,
    classified_type VARCHAR(40) NOT NULL,
    bot_response    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE analytics_events (
    id         BIGSERIAL,
    event_type VARCHAR(30) NOT NULL,
    player_id  BIGINT,
    payload    JSONB       NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE operator_reports (
    id           BIGSERIAL PRIMARY KEY,
    period_start TIMESTAMPTZ NOT NULL,
    period_end   TIMESTAMPTZ NOT NULL,
    report       JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
