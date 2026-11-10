-- 페르소나별 평균 응답시간(초). 주기 잡이 최근 대화에서 집계한다.
-- 당근의 "보통 N분 내 응답"처럼 기다림을 예측하게 하는 표시용 값이며, 게임 로직과 무관하다.
ALTER TABLE personas
    ADD COLUMN avg_response_seconds INT;
