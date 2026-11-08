"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { fetchDashboard, DashboardAuthError } from "./api";
import type { Dashboard, Range } from "./types";
import BarList from "./BarList";
import Funnel from "./Funnel";
import TrendChart from "./TrendChart";
import TypeGrid from "./TypeGrid";
import { CONTEXT_LABEL, CRIME_LABEL, FEATURE_LABEL, LEDGER_LABEL, libraryGenreLabel, num, pct, ratio } from "./format";
import styles from "./dashboard.module.css";

const TOKEN_KEY = "playloom_metrics_token";
const RANGES: { value: Range; label: string }[] = [
  { value: "today", label: "오늘" }, { value: "7d", label: "7일" },
  { value: "30d", label: "30일" }, { value: "all", label: "전체" },
];

export default function MetricsPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [range, setRange] = useState<Range>("7d");
  const [needToken, setNeedToken] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tokenInput, setTokenInput] = useState("");
  const [loadedAt, setLoadedAt] = useState<Date | null>(null);
  // 요청마다 번호를 매겨, 먼저 보낸 요청이 늦게 응답해도 마지막 요청 것만 반영한다
  const requestIdRef = useRef(0);
  // 화면 숫자가 실제로 보여 주는 기간이다, 실패하면 기간 버튼을 이 값으로 되돌린다
  const shownRangeRef = useRef<Range>("7d");

  // 첫 문장이 await 라 이펙트에서 호출해도 동기 setState 가 없다
  const load = useCallback(async (t: string, r: Range) => {
    const id = ++requestIdRef.current;
    try {
      const res = await fetchDashboard(t, r);
      if (id !== requestIdRef.current) return; // 그 사이 더 최근 요청이 있었으니 이 응답은 버린다
      setData(res);
      shownRangeRef.current = r;
      setNeedToken(false);
      setError(null);
      setLoadedAt(new Date());
    } catch (e) {
      if (id !== requestIdRef.current) return; // 마찬가지로 늦게 온 실패도 버린다
      if (e instanceof DashboardAuthError) {
        try { localStorage.removeItem(TOKEN_KEY); } catch {}
        setToken(null);
        setData(null);
        setNeedToken(true);
        setError("토큰이 올바르지 않습니다");
      } else {
        setError("지표를 불러오지 못했습니다");
        setRange(shownRangeRef.current); // 화면 숫자는 이전 기간 그대로이니 버튼도 그 기간으로 되돌린다
      }
    } finally {
      if (id === requestIdRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    // 이펙트에서 동기로 setState 하지 않으려고 async 로 감싼다, SSR 초기 렌더는 로딩 상태로 고정된다
    (async () => {
      let saved: string | null = null;
      try { saved = localStorage.getItem(TOKEN_KEY); } catch {}
      if (!saved) {
        // 토큰이 없으면 한 틱 미뤄 동기 setState 를 피한다
        await Promise.resolve();
        setNeedToken(true);
        setLoading(false);
        return;
      }
      setToken(saved);
      await load(saved, "7d");
    })();
  }, [load]);

  function submitToken(e: React.SubmitEvent<HTMLFormElement>) {
    e.preventDefault();
    const t = tokenInput.trim();
    if (!t) return;
    try { localStorage.setItem(TOKEN_KEY, t); } catch {}
    setToken(t);
    setNeedToken(false);
    setLoading(true);
    load(t, range);
  }

  function chooseRange(r: Range) {
    if (!token || r === range) return;
    setRange(r);
    setLoading(true);
    load(token, r);
  }

  function forgetToken() {
    // 기다리던 응답이 지운 토큰으로 화면을 다시 띄우지 않게 번호를 올린다
    requestIdRef.current++;
    setLoading(false);
    try { localStorage.removeItem(TOKEN_KEY); } catch {}
    setToken(null);
    setData(null);
    setNeedToken(true);
  }

  if (needToken) {
    return (
      <main className={styles.page}>
        <h1 className={styles.title}>Playalike 관리자</h1>
        <form className={styles.tokenForm} onSubmit={submitToken}>
          <label htmlFor="metrics-token">접근 토큰</label>
          <input id="metrics-token" type="password" value={tokenInput} onChange={e => setTokenInput(e.target.value)} />
          <button type="submit">열기</button>
        </form>
        {error ? <p role="alert" className={styles.error}>{error}</p> : null}
      </main>
    );
  }

  const d = data;
  const sum = (k: "testCompletes" | "libraryCompletes") => d ? d.trend.reduce((a, x) => a + x[k], 0) : 0;
  const shareClicks = d ? d.shares.taste.linkClicks + d.shares.taste.imageClicks
    + d.shares.library.linkClicks + d.shares.library.imageClicks : 0;
  const featureTotal = d ? d.distribution.features.reduce((a, x) => a + x.count, 0) : 0;
  const recordedSince = d?.distribution.libraryRecordedSince ?? null;
  // 이미 숫자가 있으면 다시 불러오는 동안 기간 자리에 진행 표시를 둔다
  let meta = "";
  if (loading && d) meta = "불러오는 중";
  else if (d) meta = `${d.from} ~ ${d.to}${loadedAt ? ` 갱신 ${loadedAt.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}` : ""}`;

  return (
    <main className={styles.page}>
      <header className={styles.topBar}>
        <h1 className={styles.title}>Playalike 관리자</h1>
        <div className={styles.ranges} role="group" aria-label="기간">
          {RANGES.map(r => (
            <button key={r.value} type="button" aria-pressed={range === r.value} onClick={() => chooseRange(r.value)}>
              {r.label}
            </button>
          ))}
        </div>
        <span className={styles.meta}>{meta}</span>
        <button type="button" className={styles.ghost} onClick={forgetToken}>토큰 지우기</button>
      </header>

      {error ? <p role="alert" className={styles.error}>{error}</p> : null}
      {loading && !d ? <p className={styles.meta}>불러오는 중</p> : null}

      {d ? (
        <>
          <section className={styles.kpis} aria-label="요약">
            <div data-testid="kpi-visitors"><span>방문자(명)</span><strong>{num(d.funnels.taste[0]?.visitors ?? 0)}</strong></div>
            <div data-testid="kpi-testCompletes"><span>테스트 완료(회)</span><strong>{num(sum("testCompletes"))}</strong></div>
            <div data-testid="kpi-libraryCompletes"><span>분석 완료(회)</span><strong>{num(sum("libraryCompletes"))}</strong></div>
            <div data-testid="kpi-shareClicks"><span>공유(회)</span><strong>{num(shareClicks)}</strong></div>
          </section>
          <p className={styles.note}>요약의 방문자는 사람 수, 나머지는 횟수입니다. 퍼널은 모두 사람 수입니다</p>

          <section className={styles.card}>
            <h2>하루별 추이</h2>
            <TrendChart days={d.trend} />
          </section>

          <div className={styles.two}>
            <div className={styles.card}><Funnel title="취향 테스트 퍼널" steps={d.funnels.taste} /></div>
            <div className={styles.card}><Funnel title="라이브러리 분석 퍼널" steps={d.funnels.library} /></div>
          </div>
          <p className={styles.note}>퍼널은 앞 단계를 거치지 않고 들어온 사람도 셉니다. 사람 수는 브라우저 기준입니다</p>

          <section className={styles.card} aria-label="결과 분포">
            <h2>결과 분포</h2>
            <h3>기능 선택</h3>
            <BarList ariaLabel="기능 선택" items={d.distribution.features.map(f => ({
              label: FEATURE_LABEL[f.code] ?? f.code, value: f.count, note: pct(f.count, featureTotal),
            }))} />
            <div className={styles.two}>
              <div><h3>취향 유형</h3><TypeGrid counts={d.distribution.tasteTypes} /></div>
              <div>
                <h3>라이브러리 판정</h3>
                {recordedSince === null ? <p className={styles.note}>판정 기록 없음</p> : (
                  <>
                    {recordedSince > d.from ? <p className={styles.note}>{recordedSince} 전의 분석에는 판정 기록이 없음</p> : null}
                    <BarList title="장부" ariaLabel="장부 판정" items={d.distribution.libraryLedger.map(c => ({
                      label: LEDGER_LABEL[c.code] ?? c.code, code: c.code, value: c.count,
                    }))} />
                    <BarList title="판결문" ariaLabel="판결문 판정" items={d.distribution.libraryCrime.map(c => ({
                      label: CRIME_LABEL[c.code] ?? c.code, code: c.code, value: c.count,
                    }))} />
                    <BarList title="장르" ariaLabel="장르 거점" items={d.distribution.libraryGenre.map(c => ({
                      label: libraryGenreLabel(c.code), code: c.code, value: c.count,
                    }))} />
                  </>
                )}
              </div>
            </div>
          </section>

          <div className={styles.two}>
            <section className={styles.card} aria-label="공유">
              <h2>공유</h2>
              <table className={styles.table}>
                <thead><tr><th>대상</th><th>링크</th><th>이미지</th><th>유입</th><th>유입/클릭</th></tr></thead>
                <tbody>
                  {([["취향", d.shares.taste], ["라이브러리", d.shares.library]] as const).map(([name, s]) => (
                    <tr key={name}><td>{name}</td><td>{num(s.linkClicks)}</td><td>{num(s.imageClicks)}</td>
                      <td>{num(s.inflows)}</td><td>{ratio(s.inflowPerClick)}</td></tr>
                  ))}
                </tbody>
              </table>
            </section>
            <section className={styles.card} aria-label="추천 반응">
              <h2>추천 반응</h2>
              <table className={styles.table}>
                <thead><tr><th>경로</th><th>노출</th><th>좋아요</th><th>싫어요</th><th>클릭</th><th>좋아요율</th></tr></thead>
                <tbody>
                  {d.recommendations.contexts.map(c => (
                    <tr key={c.context}><td title={c.context}>{CONTEXT_LABEL[c.context] ?? c.context}</td><td>{num(c.impressions)}</td><td>{num(c.up)}</td>
                      <td>{num(c.down)}</td><td>{num(c.click)}</td><td>{ratio(c.impressions > 0 ? c.upRate : null)}</td></tr>
                  ))}
                </tbody>
              </table>
              <h3>노출 상위 게임</h3>
              {d.recommendations.topGames.length === 0 ? (
                <p className={styles.note}>아직 노출 데이터가 없습니다</p>
              ) : (
                <ol className={styles.topGames}>
                  {d.recommendations.topGames.map(g => (
                    <li key={g.appid}><span>{g.name ?? `appid ${g.appid}`}</span><span>{num(g.impressions)}</span></li>
                  ))}
                </ol>
              )}
            </section>
          </div>
        </>
      ) : null}
    </main>
  );
}
