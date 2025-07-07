package SteamGame.recommend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 데이터가 이미 있는지 확인
        try {
            Integer gameCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class);

            if (gameCount != null && gameCount > 0) {
                log.info("데이터베이스에 이미 {}개의 게임이 있습니다. 초기화를 건너뜁니다.", gameCount);
                return;
            }
        } catch (Exception e) {
            log.info("게임 테이블이 존재하지 않습니다. 스키마를 생성합니다.");
        }

        log.info("데이터베이스가 비어있습니다. 초기 데이터를 로드합니다...");

        try {
            // 기존 데이터 완전 삭제 (혹시 남아있을 수 있는 데이터)
            cleanupExistingData();

            // 스키마 실행
            executeSchemaIfNeeded();

            // 데이터 실행 (트랜잭션 처리)
            executeDataScript();

            // 최종 확인
            Integer finalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class);
            log.info("데이터베이스 초기화 완료! 총 {}개 게임 로드됨", finalCount);

        } catch (Exception e) {
            log.error("데이터베이스 초기화 중 오류 발생", e);
            throw e;
        }
    }

    private void cleanupExistingData() {
        try {
            log.info("기존 데이터 정리 중...");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS FALSE");
            jdbcTemplate.execute("DELETE FROM game_tags");
            jdbcTemplate.execute("DELETE FROM tag_cooccurrence");
            jdbcTemplate.execute("DELETE FROM reviews");
            jdbcTemplate.execute("DELETE FROM tags");
            jdbcTemplate.execute("DELETE FROM games");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS TRUE");
            log.info("기존 데이터 정리 완료");
        } catch (Exception e) {
            log.warn("기존 데이터 정리 중 오류 (무시): {}", e.getMessage());
        }
    }

    private void executeSchemaIfNeeded() throws Exception {
        try {
            // games 테이블이 있는지 확인
            jdbcTemplate.queryForObject("SELECT 1 FROM games LIMIT 1", Integer.class);
            log.info("테이블이 이미 존재합니다.");
        } catch (Exception e) {
            log.info("테이블을 생성합니다...");
            executeScript("schema.sql");
        }
    }

    private void executeDataScript() throws Exception {
        log.info("게임 데이터를 삽입합니다... (약 8000개 게임, 시간이 걸릴 수 있습니다)");
        long startTime = System.currentTimeMillis();

        try {
            // H2 설정 - MySQL 호환 모드
            jdbcTemplate.execute("SET MODE MySQL");
            jdbcTemplate.execute("SET AUTOCOMMIT FALSE");

            // H2에서는 AUTO_INCREMENT 컬럼을 제외한 INSERT가 자동으로 처리됨
            // 별도의 IDENTITY_INSERT 설정 불필요

            executeScript("data.sql");

            // 명시적 커밋
            jdbcTemplate.execute("COMMIT");
            log.info("트랜잭션 커밋 완료");

        } catch (Exception e) {
            // 오류 시 롤백
            try {
                jdbcTemplate.execute("ROLLBACK");
                log.error("트랜잭션 롤백됨");
            } catch (Exception rollbackException) {
                log.error("롤백 중 오류: {}", rollbackException.getMessage());
            }
            throw e;
        } finally {
            try {
                jdbcTemplate.execute("SET AUTOCOMMIT TRUE");
            } catch (Exception finallyException) {
                log.error("Finally 블록 오류: {}", finallyException.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("데이터 삽입 완료! 소요시간: {}초", (endTime - startTime) / 1000.0);
    }

    private void executeScript(String scriptPath) throws Exception {
        ClassPathResource resource = new ClassPathResource(scriptPath);
        String script = FileCopyUtils.copyToString(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
        );

        // 외래키 체크 비활성화 (data.sql 실행 전)
        if ("data.sql".equals(scriptPath)) {
            log.info("외래키 체크 비활성화");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS FALSE");
        }

        // SQL 스크립트를 세미콜론으로 분할하여 실행
        String[] statements = script.split(";");

        int executedCount = 0;
        int errorCount = 0;
        String currentTable = "";

        for (String statement : statements) {
            statement = statement.trim();
            if (!statement.isEmpty() && !statement.startsWith("--")) {

                // 현재 처리 중인 테이블 파악
                if (statement.toUpperCase().contains("INSERT INTO")) {
                    String tableName = statement.substring(statement.toUpperCase().indexOf("INSERT INTO") + 12);
                    tableName = tableName.substring(0, tableName.indexOf(" ")).trim();
                    if (!tableName.equals(currentTable)) {
                        currentTable = tableName;
                        log.info("테이블 처리 시작: {}", currentTable);
                    }
                }

                try {
                    jdbcTemplate.execute(statement);
                    executedCount++;

                    // 진행상황 로깅
                    if (statement.toUpperCase().startsWith("INSERT") && executedCount % 500 == 0) {
                        log.info("[{}] 데이터 삽입 진행중... {}개 완료", currentTable, executedCount);
                    }

                } catch (Exception e) {
                    errorCount++;

                    // 첫 번째 오류는 상세 로그
                    if (errorCount <= 3) {
                        log.error("=== SQL 실행 실패 상세 정보 ===");
                        log.error("테이블: {}", currentTable);
                        log.error("실행 문장: {}", statement.substring(0, Math.min(200, statement.length())));
                        log.error("오류 메시지: {}", e.getMessage());
                        log.error("=====================================");
                    }

                    // games나 reviews 테이블 오류는 치명적
                    if (currentTable.equals("games") || currentTable.equals("reviews")) {
                        log.error("핵심 테이블 ({}) 데이터 삽입 실패! 초기화 중단", currentTable);
                        throw new RuntimeException("핵심 데이터 삽입 실패: " + currentTable, e);
                    }

                    // tag_cooccurrence 중복은 정상 (무시)
                    if (!currentTable.equals("tag_cooccurrence")) {
                        log.warn("[{}] SQL 실행 실패 ({}번째): {}", currentTable, errorCount, e.getMessage());
                    }
                }
            }
        }

        // 외래키 체크 복원 (data.sql 실행 후)
        if ("data.sql".equals(scriptPath)) {
            log.info("외래키 체크 복원");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS TRUE");
        }

        log.info("{} 스크립트 실행 완료 - {}개 성공, {}개 실패", scriptPath, executedCount, errorCount);

        // 핵심 테이블 데이터 확인
        if ("data.sql".equals(scriptPath)) {
            checkCoreTableData();
        }
    }

    private void checkCoreTableData() {
        try {
            Integer games = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM games", Integer.class);
            Integer reviews = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reviews", Integer.class);

            log.info("핵심 테이블 데이터 확인 - games: {}개, reviews: {}개", games, reviews);

            if (games == 0) {
                throw new RuntimeException("games 테이블에 데이터가 없습니다!");
            }
            if (reviews == 0) {
                throw new RuntimeException("reviews 테이블에 데이터가 없습니다!");
            }

        } catch (Exception e) {
            log.error("핵심 테이블 데이터 확인 실패", e);
            throw new RuntimeException("핵심 데이터 검증 실패", e);
        }
    }
}