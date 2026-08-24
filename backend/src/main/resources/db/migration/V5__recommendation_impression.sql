-- 추천으로 노출된 게임 로그: 응답에 실린 appid 를 익명 방문자(client_id) 단위로 적재
CREATE TABLE recommendation_impression (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id     VARCHAR(64) NOT NULL,          -- localStorage UUID (익명 방문자 키)
    steam_id      VARCHAR(32),                   -- profile/recent 플로우에서만 존재
    appid         BIGINT      NOT NULL,
    context       VARCHAR(20) NOT NULL,          -- INPUT/PROFILE/RECENT/SIMILAR/RANDOM/MATCH
    rank_position INTEGER,                       -- 결과 목록 내 1-based 순위
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_impression_context CHECK (context IN ('INPUT','PROFILE','RECENT','SIMILAR','RANDOM','MATCH'))
);

CREATE INDEX idx_impression_client_id  ON recommendation_impression(client_id);
CREATE INDEX idx_impression_appid      ON recommendation_impression(appid);
CREATE INDEX idx_impression_created_at ON recommendation_impression(created_at);
