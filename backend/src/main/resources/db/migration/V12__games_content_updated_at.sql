-- 임베딩 글에 들어가는 내용(이름, 설명, 장르)이 실제로 바뀐 시각을 남긴다
-- 재임베딩 기준을 상세 수집의 last_crawled_at 에서 이 시각으로 옮긴다, 리뷰만 다시 받고도 같은 글을 다시 임베딩하던 낭비를 막는다
-- 비어 있어도 되는 칼럼 추가라 테이블을 다시 쓰지 않는다, 기존 게임은 모두 null 이라 배포 직후 한꺼번에 재임베딩하지 않는다
ALTER TABLE games ADD COLUMN content_updated_at TIMESTAMPTZ;
