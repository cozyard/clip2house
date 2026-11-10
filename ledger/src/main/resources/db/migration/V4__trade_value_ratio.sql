-- 성사 관측: 수락된 Offer의 제안자와 '페르소나 관점 가치 비율'을 거래에 남긴다.
-- persona_value_ratio = (페르소나가 받은 물건 가치) / (페르소나가 내준 매물 가치).
-- 1.0 미만이면 페르소나가 손해 본 성사다. 인젝션·사회공학은 입력·상태 계층에 흔적을 안 남기고
-- '페르소나 발의 + 손해 비율' 조합으로만 드러나므로, 사후 관측·감사를 위해 성사에 직접 기록한다.
-- 게임 로직과는 무관한 관측용 필드다. (05-evaluation-observability.md)
ALTER TABLE trades
    ADD COLUMN offer_proposer      VARCHAR(8),
    ADD COLUMN persona_value_ratio NUMERIC(12, 4);
