-- 라이브러리 분석 판정(장부, 판결문, 장르 거점 1위)을 LIBRARY_COMPLETE 이벤트에만 남긴다
-- 비어 있어도 되는 칼럼 추가라 테이블을 다시 쓰지 않는다, 기존 이벤트는 모두 null 이라 CHECK 검증이 바로 통과한다
ALTER TABLE funnel_event
    ADD COLUMN ledger_code VARCHAR(32),
    ADD COLUMN crime_code  VARCHAR(32),
    ADD COLUMN genre_code  VARCHAR(32);

ALTER TABLE funnel_event ADD CONSTRAINT chk_funnel_event_library_codes CHECK (
    event_type = 'LIBRARY_COMPLETE'
    OR (ledger_code IS NULL AND crime_code IS NULL AND genre_code IS NULL));
