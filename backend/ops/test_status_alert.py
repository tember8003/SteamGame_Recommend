import unittest
from datetime import datetime, timezone

import status_alert as sa


def status(warnings=(), rev="aaaaaaa1", started="2026-11-23T08:10:00Z", five_xx=0, recent=()):
    return {
        "server": {"revision": rev, "startedAt": started, "flywayVersion": "12"},
        "errors": {"counts": [{"status": 500, "errorCode": "INTERNAL", "count": five_xx}] if five_xx else [],
                   "recent": list(recent)},
        "collection": {"warnings": [{"code": c, "batchType": b} for c, b in warnings], "jobs": [], "data": {}},
    }


BASE = {"issues": [], "fetch_fails": 0, "revision": "aaaaaaa1", "started_at": "2026-11-23T08:10:00Z", "five_xx": 0}


class EvaluateTest(unittest.TestCase):

    def test_quiet_when_nothing_changes(self):
        lines, state = sa.evaluate(status(), None, 40, BASE)
        self.assertEqual(lines, [])
        self.assertEqual(state["issues"], [])

    def test_new_warning_is_sent_once(self):
        lines, state = sa.evaluate(status([("JOB_FAILED", "detail_rolling")]), None, 40, BASE)
        self.assertEqual(lines, ["[경고] 상세 수집 마지막 실행 실패"])
        again, _ = sa.evaluate(status([("JOB_FAILED", "detail_rolling")]), None, 40, state)
        self.assertEqual(again, [])

    def test_resolved_warning(self):
        prev = dict(BASE, issues=["JOB_STALE:embed_backfill"])
        lines, state = sa.evaluate(status(), None, 40, prev)
        self.assertEqual(lines, ["[해결] 임베딩 주기 초과"])
        self.assertEqual(state["issues"], [])

    def test_single_fetch_failure_is_not_alerted(self):
        lines, state = sa.evaluate(None, "UNREACHABLE", 40, BASE)
        self.assertEqual(lines, [])
        self.assertEqual(state["fetch_fails"], 1)
        lines, state = sa.evaluate(None, "UNREACHABLE", 40, state)
        self.assertEqual(lines, ["[경고] 상태 API 를 부르지 못함 (앱이나 Caddy 확인)"])
        lines, _ = sa.evaluate(status(), None, 40, state)
        self.assertEqual(lines, ["[해결] 상태 API 를 부르지 못함 (앱이나 Caddy 확인)"])

    def test_fetch_failure_keeps_backend_warnings(self):
        # 못 부른 동안 경고가 풀렸다고 잘못 알리지 않음
        prev = dict(BASE, issues=["JOB_FAILED:detail_rolling"])
        lines, state = sa.evaluate(None, "UNREACHABLE", 40, prev)
        self.assertEqual(lines, [])
        self.assertEqual(state["issues"], ["JOB_FAILED:detail_rolling"])
        self.assertEqual(state["started_at"], BASE["started_at"])

    def test_token_rejected_is_alerted_immediately(self):
        lines, _ = sa.evaluate(None, "TOKEN_REJECTED", 40, BASE)
        self.assertEqual(lines, ["[경고] 상태 API 가 토큰을 거부함 (APP_METRICS_TOKEN 확인)"])

    def test_deploy_and_restart_are_told_apart(self):
        deploy, _ = sa.evaluate(status(rev="bbbbbbb2", started="2026-11-28T01:00:00Z"), None, 40, BASE)
        self.assertEqual(len(deploy), 1)
        self.assertTrue(deploy[0].startswith("[알림] 배포됨 `bbbbbbb`"))
        restart, _ = sa.evaluate(status(started="2026-11-28T01:00:00Z"), None, 40, BASE)
        self.assertIn("다시 시작됨", restart[0])

    def test_five_xx_increase(self):
        lines, state = sa.evaluate(status(five_xx=3, recent=[{"status": 500, "route": "/api/match"}]), None, 40, BASE)
        self.assertEqual(lines, ["[알림] 5xx 3건 늘어남 (최근: 500 /api/match)"])
        again, _ = sa.evaluate(status(five_xx=3), None, 40, state)
        self.assertEqual(again, [])

    def test_five_xx_counter_reset_by_restart_is_not_increase(self):
        prev = dict(BASE, five_xx=10)
        lines, _ = sa.evaluate(status(rev="bbbbbbb2", started="2026-11-28T01:00:00Z", five_xx=0), None, 40, prev)
        self.assertEqual(len(lines), 1)
        self.assertIn("배포됨", lines[0])

    def test_disk_threshold(self):
        self.assertEqual(sa.evaluate(status(), None, 84, BASE)[0], [])
        lines, _ = sa.evaluate(status(), None, 85, BASE)
        self.assertEqual(lines, ["[경고] 디스크 사용량 85% 초과"])

    def test_first_run_has_no_events(self):
        lines, state = sa.evaluate(status(), None, 40, {})
        self.assertEqual(lines, [])
        self.assertEqual(state["revision"], "aaaaaaa1")


class SummaryTest(unittest.TestCase):

    def test_summary_lists_jobs_and_data(self):
        s = status()
        s["collection"]["jobs"] = [{"batchType": "detail_rolling", "lastSuccessAt": "2026-11-27T12:30:00Z",
                                    "lastNote": "withText=992 noText=8 malformed=0 failed=0",
                                    "runs7d": 28, "failedRuns7d": 0}]
        s["collection"]["data"] = {"games": 40122, "reviewTextWrites24h": 3982}
        text = sa.summary_text(s, None, 41, now=datetime(2026, 11, 28, 0, 0, tzinfo=timezone.utc))
        self.assertIn("2026-11-28 09:00 KST", text)
        self.assertIn("[정상] 경고 없음", text)
        self.assertIn("ok 상세 수집", text)
        self.assertIn("11-27 21:30", text)
        self.assertIn("게임 40,122, 24시간 리뷰 저장 3,982", text)
        self.assertIn("(4일째)", text)

    def test_summary_when_unreachable(self):
        text = sa.summary_text(None, "UNREACHABLE", 41)
        self.assertIn("[경고] 상태 API 를 부르지 못함", text)


class ReadEnvTest(unittest.TestCase):

    def test_missing_file_is_empty(self):
        self.assertEqual(sa.read_env("/nonexistent/.env"), {})


if __name__ == "__main__":
    unittest.main()
