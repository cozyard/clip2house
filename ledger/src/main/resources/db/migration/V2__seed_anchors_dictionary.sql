-- Golden Anchor 11종: 클립 100원부터 아파트 8억 원까지 log 스케일로 가치 범위를 덮는다.
-- 사람이 검수한 불변 세트이며 생성값 검증의 기준이다. (01-game-design.md / 07-architecture-stack.md)
INSERT INTO golden_anchors (name, ref_value) VALUES
    ('클립', 100),
    ('볼펜 세트', 1000),
    ('백반 한 끼', 10000),
    ('무선 이어폰', 100000),
    ('최신 스마트폰', 1000000),
    ('중고 경차', 10000000),
    ('신형 중형 세단', 40000000),
    ('원룸 전세 보증금', 100000000),
    ('지방 아파트', 300000000),
    ('수도권 아파트', 550000000),
    ('서울 아파트', 800000000);

-- 시드 사전: 오케스트레이터 없이도 Faucet이 즉시 동작하도록 가치 범위를 덮는 사람 작성 엔트리.
-- Provenance는 human/seed-v1로 남겨 생성 코호트와 구분한다.
INSERT INTO items (name, name_key, description, ref_value, gen_version, gen_model, gen_prompt_id, cited_anchor_id)
VALUES
    ('빨간 클립', 'starter-clip', '평범한 빨간색 종이 클립입니다. 모든 이야기가 여기서 시작됩니다.', 100,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '클립')),
    ('곰돌이 머리끈', 'gomdori-hairband', '곰돌이 장식이 달린 머리끈이에요. 몇 번 안 썼습니다.', 500,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '볼펜 세트')),
    ('형광펜 5색 세트', 'highlighter-set', '새것 같은 형광펜 세트입니다. 한 자루만 개봉했어요.', 3000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '볼펜 세트')),
    ('스테인리스 텀블러', 'steel-tumbler', '보온 잘 되는 텀블러입니다. 세척해서 드려요.', 12000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '백반 한 끼')),
    ('무선 마우스', 'wireless-mouse', '로지텍 무선 마우스, 사용감 약간 있습니다.', 18000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '백반 한 끼')),
    ('보조배터리 20000mAh', 'power-bank', '대용량 보조배터리입니다. 배터리 상태 좋아요.', 30000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '백반 한 끼')),
    ('접이식 캠핑 의자', 'camping-chair', '한 시즌 쓴 캠핑 의자입니다. 프레임 튼튼합니다.', 45000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '무선 이어폰')),
    ('캡슐 커피머신', 'capsule-coffee-machine', '캡슐 커피머신, 정상 작동합니다. 캡슐 몇 개 드려요.', 130000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '무선 이어폰')),
    ('27인치 게이밍 모니터', 'gaming-monitor', '144Hz 게이밍 모니터입니다. 불량화소 없습니다.', 320000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '무선 이어폰')),
    ('로드 자전거', 'road-bike', '입문용 로드 자전거입니다. 최근 정비 완료했습니다.', 850000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '최신 스마트폰')),
    ('최신형 노트북', 'latest-laptop', '작년에 산 고사양 노트북입니다. 배터리 사이클 적어요.', 1800000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '최신 스마트폰')),
    ('미러리스 카메라', 'mirrorless-camera', '풀프레임 미러리스 바디+렌즈 세트입니다.', 2600000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '최신 스마트폰')),
    ('명품 핸드백', 'luxury-handbag', '정품 보증서 있는 명품 핸드백입니다. 상태 A급.', 4200000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '중고 경차')),
    ('중고 스쿠터', 'used-scooter', '125cc 스쿠터입니다. 주행거리 짧고 관리 잘 했습니다.', 1500000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '최신 스마트폰')),
    ('중고 경차', 'used-city-car', '2019년식 경차입니다. 무사고, 정기점검 기록 있습니다.', 7000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '중고 경차')),
    ('중고 SUV', 'used-suv', '패밀리 SUV입니다. 실내 깨끗하고 타이어 새로 갈았습니다.', 19000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '중고 경차')),
    ('캠핑 트레일러', 'camping-trailer', '풀옵션 캠핑 트레일러입니다. 즉시 사용 가능합니다.', 38000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '신형 중형 세단')),
    ('오피스텔 전세권', 'officetel-jeonse', '역세권 오피스텔 전세 자리입니다. 승계 조건 협의 가능합니다.', 120000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '원룸 전세 보증금')),
    ('시골 단독주택', 'country-house', '마당 있는 시골 단독주택입니다. 리모델링 일부 완료.', 180000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '지방 아파트')),
    ('신축 아파트 분양권', 'apartment-right', '신축 아파트 분양권입니다. 입주 예정 단지입니다.', 420000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '수도권 아파트')),
    ('한강뷰 아파트', 'riverview-apartment', '한강이 보이는 아파트입니다. 이보다 나은 목표는 없습니다.', 850000000,
     'seed-v1', 'human', 'seed', (SELECT id FROM golden_anchors WHERE name = '서울 아파트'));

-- 첫 시즌. 이후 롤오버는 시즌 스케줄러가 만든다.
INSERT INTO seasons (starts_at, ends_at) VALUES (now(), now() + INTERVAL '56 days');
