-- SteamSpy 보유자 추정치, 카탈로그를 스팀 Query 로 바꾼 뒤 채울 출처가 없어 지움
ALTER TABLE games DROP COLUMN owners_estimate;
