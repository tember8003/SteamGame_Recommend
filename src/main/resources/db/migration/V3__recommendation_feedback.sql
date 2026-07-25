-- 추천 피드백 로그: 노출 게임에 대한 반응(👍/👎/클릭)을 익명 방문자(client_id) 단위로 적재
CREATE TABLE recommendation_feedback (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id     VARCHAR(64)  NOT NULL,          -- localStorage UUID (익명 방문자 키)
    steam_id      VARCHAR(32),                    -- profile/recent 플로우에서만 존재
    appid         BIGINT       NOT NULL,
    context       VARCHAR(20)  NOT NULL,          -- INPUT/PROFILE/RECENT/SIMILAR/RANDOM
    signal        VARCHAR(10)  NOT NULL,          -- UP/DOWN/CLICK
    rank_position INTEGER,                        -- 결과 목록 내 순위(learning-to-rank용)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_feedback_signal  CHECK (signal  IN ('UP','DOWN','CLICK')),
    CONSTRAINT chk_feedback_context CHECK (context IN ('INPUT','PROFILE','RECENT','SIMILAR','RANDOM'))
);

CREATE INDEX idx_feedback_client_id  ON recommendation_feedback(client_id);
CREATE INDEX idx_feedback_appid      ON recommendation_feedback(appid);
CREATE INDEX idx_feedback_created_at ON recommendation_feedback(created_at);
