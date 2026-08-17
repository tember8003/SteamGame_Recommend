-- MATCH 컨텍스트를 허용하도록 CHECK 를 넓힌다, 기존 행에는 영향 없음
ALTER TABLE recommendation_feedback DROP CONSTRAINT chk_feedback_context;
ALTER TABLE recommendation_feedback ADD CONSTRAINT chk_feedback_context
    CHECK (context IN ('INPUT','PROFILE','RECENT','SIMILAR','RANDOM','MATCH'));
