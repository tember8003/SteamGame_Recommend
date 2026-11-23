-- 상세 수집이 리뷰 본문을 실제로 저장한 시각을 남긴다
-- 수집 작업이 스스로 보고한 숫자와 별개로, 최근에 실제로 저장된 본문 수를 세는 근거로 쓴다
-- 비어 있어도 되는 칼럼 추가라 테이블을 다시 쓰지 않는다, 기존 리뷰는 모두 null 로 남는다
ALTER TABLE reviews ADD COLUMN text_updated_at TIMESTAMPTZ;
