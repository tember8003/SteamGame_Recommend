#!/usr/bin/env python3
"""운영 상태를 디스코드로 알린다.

check   : 10분마다 돈다. 경고가 새로 생기거나 풀릴 때, 배포, 재시작, 5xx 증가가 있을 때만 보낸다
summary : 하루 한 번 돈다. 이상이 없어도 지금 상태를 짧게 보낸다

경고 판정은 백엔드(/api/metrics/status 의 collection.warnings)가 한다, 여기서는 전달하고 상태 변화만 기억한다
서버가 통째로 죽은 경우는 UptimeRobot 이 맡는다, 이 스크립트는 서버 안에서 돌아서 같이 죽기 때문
표준 라이브러리만 쓴다, 서버에 따로 설치할 것이 없게 함
"""
import json
import os
import shutil
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
APP_ENV = os.environ.get("SGR_APP_ENV", os.path.join(BASE_DIR, "..", ".env"))
ALERT_ENV = os.environ.get("SGR_ALERT_ENV", os.path.join(BASE_DIR, "alert.env"))
STATE_FILE = os.environ.get("SGR_ALERT_STATE", os.path.join(BASE_DIR, "alert-state.json"))

KST = timezone(timedelta(hours=9))
DISK_LIMIT_PCT = 85
# 한 번 못 부른 것은 배포 재시작이나 순간 끊김일 수 있어 두 번 연속일 때만 알림
UNREACHABLE_AFTER = 2
DISCORD_MAX = 1900

JOB_LABEL = {
    "steam_catalog": "카탈로그 수집",
    "tag_cooccurrence": "태그 쌍",
    "detail_rolling": "상세 수집",
    "embed_backfill": "임베딩",
    "tag_embedding": "GNN 학습",
}


def job_label(batch_type):
    return JOB_LABEL.get(batch_type, batch_type or "")


# 문구는 프론트 대시보드(format.ts 의 warningText)와 맞춘다
def issue_text(key):
    code, _, batch = key.partition(":")
    job = job_label(batch)
    return {
        "JOB_STUCK": f"{job} 24시간 넘게 끝나지 않음 (멈춤 추정)",
        "JOB_STALE": f"{job} 주기 초과",
        "JOB_FAILED": f"{job} 마지막 실행 실패",
        "CATALOG_DROP": "카탈로그 저장 수 5% 넘게 감소",
        "DETAIL_NO_WRITES": "상세 수집이 돌았는데 24시간 동안 저장된 리뷰 본문 0",
        "UNREACHABLE": "상태 API 를 부르지 못함 (앱이나 Caddy 확인)",
        "TOKEN_REJECTED": "상태 API 가 토큰을 거부함 (APP_METRICS_TOKEN 확인)",
        "DISK_HIGH": f"디스크 사용량 {DISK_LIMIT_PCT}% 초과",
    }.get(code, key)


def read_env(path):
    values = {}
    try:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                values[k.strip()] = v.strip().strip('"').strip("'")
    except FileNotFoundError:
        pass
    return values


def parse_time(s):
    if not s:
        return None
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None


def kst(s):
    t = parse_time(s)
    return t.astimezone(KST).strftime("%m-%d %H:%M") if t else "-"


def fetch_status(domain, token):
    """(상태 dict, 오류 코드) 를 돌려준다, 성공이면 오류 코드는 None"""
    req = urllib.request.Request(
        f"https://{domain}/api/metrics/status",
        headers={"X-Metrics-Token": token, "User-Agent": "sgr-status-alert/1.0"},
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as res:
            return json.load(res), None
    except urllib.error.HTTPError as e:
        return None, "TOKEN_REJECTED" if e.code == 401 else "UNREACHABLE"
    except Exception:
        return None, "UNREACHABLE"


def disk_pct(path="/"):
    u = shutil.disk_usage(path)
    return round(u.used * 100 / u.total)


def five_xx(status):
    counts = (status.get("errors") or {}).get("counts") or []
    return sum(c.get("count", 0) for c in counts if c.get("status", 0) >= 500)


def evaluate(status, fetch_error, disk, prev):
    """이번 검사에서 보낼 줄들과 다음에 기억할 상태를 돌려준다, 입출력 없는 순수 함수라 시험한다"""
    prev_issues = set(prev.get("issues", []))
    fails = prev.get("fetch_fails", 0)
    issues = set()
    events = []
    state = {}

    if fetch_error:
        fails += 1
        # 못 불렀을 때는 백엔드 경고를 알 수 없으니 직전 경고를 그대로 둔다, 풀렸다고 잘못 알리지 않게 함
        issues |= {i for i in prev_issues if i.split(":")[0] not in ("UNREACHABLE", "TOKEN_REJECTED")}
        if fetch_error == "TOKEN_REJECTED" or fails >= UNREACHABLE_AFTER:
            issues.add(fetch_error)
        for k in ("revision", "started_at", "five_xx"):
            if k in prev:
                state[k] = prev[k]
    else:
        fails = 0
        for w in (status.get("collection") or {}).get("warnings") or []:
            issues.add(f"{w.get('code')}:{w.get('batchType') or ''}")

        server = status.get("server") or {}
        rev, started = server.get("revision"), server.get("startedAt")
        xx = five_xx(status)
        if prev.get("started_at") and started != prev.get("started_at"):
            if rev != prev.get("revision"):
                events.append(f"배포됨 `{(rev or '?')[:7]}` (기동 {kst(started)})")
            else:
                events.append(f"앱이 다시 시작됨 (같은 버전 `{(rev or '?')[:7]}`, 기동 {kst(started)}), 메모리 초과나 수동 재시작 확인")
        elif prev.get("started_at") and xx > prev.get("five_xx", 0):
            recent = [e for e in (status.get("errors") or {}).get("recent") or [] if e.get("status", 0) >= 500][:3]
            detail = ", ".join(f"{e.get('status')} {e.get('route')}" for e in recent)
            events.append(f"5xx {xx - prev.get('five_xx', 0)}건 늘어남" + (f" (최근: {detail})" if detail else ""))
        state.update(revision=rev, started_at=started, five_xx=xx)

    if disk is not None and disk >= DISK_LIMIT_PCT:
        issues.add("DISK_HIGH")

    lines = []
    for key in sorted(issues - prev_issues):
        lines.append(f"[경고] {issue_text(key)}")
    for key in sorted(prev_issues - issues):
        lines.append(f"[해결] {issue_text(key)}")
    lines += [f"[알림] {e}" for e in events]

    state.update(issues=sorted(issues), fetch_fails=fails)
    return lines, state


def summary_text(status, fetch_error, disk, now=None):
    now = now or datetime.now(timezone.utc)
    head = f"**Playalike 일일 상태** {now.astimezone(KST).strftime('%Y-%m-%d %H:%M')} KST"
    if fetch_error:
        return f"{head}\n[경고] {issue_text(fetch_error)}\n디스크 {disk}%"

    server = status.get("server") or {}
    coll = status.get("collection") or {}
    data = coll.get("data") or {}
    warnings = coll.get("warnings") or []
    started = parse_time(server.get("startedAt"))
    uptime = f"{(now - started).days}일째" if started else "-"

    lines = [head]
    lines.append(f"[경고] {len(warnings)}건" if warnings else "[정상] 경고 없음")
    for w in warnings:
        lines.append(f"  - {issue_text(w.get('code', '') + ':' + (w.get('batchType') or ''))}")
    lines.append(f"버전 `{(server.get('revision') or '?')[:7]}`, 기동 {kst(server.get('startedAt'))} ({uptime}), "
                 f"Flyway V{server.get('flywayVersion') or '?'}, 5xx {five_xx(status)}건(기동 뒤), 디스크 {disk}%")
    lines.append("```")
    for j in coll.get("jobs") or []:
        mark = "!!" if (j.get("lastRunFailed") or j.get("stale") or j.get("stuck")) else "ok"
        note = (j.get("lastNote") or "")[:48]
        lines.append(f"{mark} {job_label(j.get('batchType')):<7} 성공 {kst(j.get('lastSuccessAt'))}  7일 {j.get('runs7d', 0)}회/실패 {j.get('failedRuns7d', 0)}  {note}")
    lines.append("```")
    lines.append(f"게임 {data.get('games', 0):,}, 24시간 리뷰 저장 {data.get('reviewTextWrites24h', 0):,}, "
                 f"임베딩 없음 {data.get('embeddingMissing', 0):,}, GNN 벡터 없음 {data.get('tagVectorMissing', 0):,}")
    lines.append(f"마지막 이벤트 {kst(data.get('lastFunnelEventAt'))}, 마지막 추천 노출 {kst(data.get('lastImpressionAt'))}")
    return "\n".join(lines)


def post_discord(webhook, text):
    if len(text) > DISCORD_MAX:
        text = text[:DISCORD_MAX] + "\n…(잘림)"
    req = urllib.request.Request(
        webhook,
        data=json.dumps({"content": text, "allowed_mentions": {"parse": []}}).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": "sgr-status-alert/1.0"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=20) as res:
        res.read()


def load_state():
    try:
        with open(STATE_FILE, encoding="utf-8") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def save_state(state):
    tmp = STATE_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(state, f, ensure_ascii=False)
    os.replace(tmp, STATE_FILE)


def main(argv):
    mode = argv[1] if len(argv) > 1 else "check"
    if mode not in ("check", "summary"):
        print("usage: status_alert.py [check|summary]", file=sys.stderr)
        return 2

    app_env, alert_env = read_env(APP_ENV), read_env(ALERT_ENV)
    domain = alert_env.get("STATUS_DOMAIN") or app_env.get("APP_DOMAIN")
    token = app_env.get("APP_METRICS_TOKEN")
    webhook = alert_env.get("DISCORD_WEBHOOK_URL")
    if not (domain and token and webhook):
        # 값 자체는 찍지 않는다, 어느 것이 빠졌는지만 남김
        missing = [n for n, v in (("APP_DOMAIN", domain), ("APP_METRICS_TOKEN", token), ("DISCORD_WEBHOOK_URL", webhook)) if not v]
        print(f"설정 누락: {', '.join(missing)}", file=sys.stderr)
        return 2

    status, err = fetch_status(domain, token)
    disk = disk_pct()

    if mode == "summary":
        post_discord(webhook, summary_text(status, err, disk))
        return 0

    prev = load_state()
    lines, state = evaluate(status, err, disk, prev)
    if lines and prev:
        post_discord(webhook, "\n".join(lines))
    elif lines:
        # 처음 실행은 기준만 잡는다, 이미 있던 경고는 알린다
        first = [l for l in lines if l.startswith("[경고]")]
        if first:
            post_discord(webhook, "\n".join(first))
    # 보내기에 실패하면 상태를 저장하지 않아 다음 실행에서 다시 보낸다
    save_state(state)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
