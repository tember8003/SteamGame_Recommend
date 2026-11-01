-- 라이브러리 분석 완료 이벤트(LIBRARY_COMPLETE)를 퍼널 이벤트로 허용한다, 기존 데이터에는 영향 없음
-- NOT VALID 로 걸고 같은 파일에서 VALIDATE 한다, Flyway 는 마이그레이션 하나를 한 트랜잭션으로 돌려서 잠금 시간은 줄지 않는다
-- 지금은 funnel_event 가 작아 감수한다, 커지면 VALIDATE 를 뒤 마이그레이션으로 나눈다
ALTER TABLE funnel_event DROP CONSTRAINT chk_funnel_event_type;
ALTER TABLE funnel_event ADD CONSTRAINT chk_funnel_event_type CHECK (event_type IN
    ('LANDING_VISIT','FEATURE_SELECT','TEST_START','TEST_COMPLETE','SHARE_CLICK','SHARE_INFLOW','LIBRARY_COMPLETE')) NOT VALID;
ALTER TABLE funnel_event VALIDATE CONSTRAINT chk_funnel_event_type;

-- 라이브러리 분석 결과 공유 저장, 만료되면 조회에서 빠지고 새벽 정리 작업이 지운다
-- Steam ID 는 저장하지 않는다, share_id 는 임의 16바이트를 base64url 로 쓴 22자
CREATE TABLE library_share (
    share_id   VARCHAR(22) PRIMARY KEY,
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_library_share_expires ON library_share(expires_at);
