-- 취향 테스트 추천(TASTE)을 노출 기록과 피드백의 컨텍스트로 허용한다, 기존 데이터에는 영향 없음
-- NOT VALID 로 걸고 같은 파일에서 VALIDATE 한다, Flyway 는 마이그레이션 하나를 한 트랜잭션으로 돌려서 잠금 시간은 줄지 않는다
-- 지금은 두 테이블이 작아 감수한다, 커지면 VALIDATE 를 뒤 마이그레이션으로 나눈다
ALTER TABLE recommendation_impression DROP CONSTRAINT chk_impression_context;
ALTER TABLE recommendation_impression ADD CONSTRAINT chk_impression_context
    CHECK (context IN ('INPUT','PROFILE','RECENT','SIMILAR','RANDOM','MATCH','TASTE')) NOT VALID;
ALTER TABLE recommendation_impression VALIDATE CONSTRAINT chk_impression_context;

ALTER TABLE recommendation_feedback DROP CONSTRAINT chk_feedback_context;
ALTER TABLE recommendation_feedback ADD CONSTRAINT chk_feedback_context
    CHECK (context IN ('INPUT','PROFILE','RECENT','SIMILAR','RANDOM','MATCH','TASTE')) NOT VALID;
ALTER TABLE recommendation_feedback VALIDATE CONSTRAINT chk_feedback_context;

-- 취향 테스트 퍼널 이벤트: 방문, 기능 선택, 시작, 완료, 공유, 공유 유입
-- IP, User-Agent, Steam ID, 답은 저장하지 않는다
CREATE TABLE funnel_event (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id  VARCHAR(64),                      -- 익명 방문자 키, 없으면 null
    event_type VARCHAR(20) NOT NULL,             -- LANDING_VISIT/FEATURE_SELECT/TEST_START/TEST_COMPLETE/SHARE_CLICK/SHARE_INFLOW
    ref        VARCHAR(32),                      -- 기능 이름 또는 유형 코드
    channel    VARCHAR(10),                      -- LINK/IMAGE, 공유 클릭에서만 존재
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_funnel_event_type CHECK (event_type IN
        ('LANDING_VISIT','FEATURE_SELECT','TEST_START','TEST_COMPLETE','SHARE_CLICK','SHARE_INFLOW')),
    CONSTRAINT chk_funnel_event_channel CHECK (channel IS NULL OR channel IN ('LINK','IMAGE'))
);

CREATE INDEX idx_funnel_event_type_created ON funnel_event(event_type, created_at);
