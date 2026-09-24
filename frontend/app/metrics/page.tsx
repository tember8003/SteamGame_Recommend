"use client";

import { useCallback, useEffect, useState } from "react";
import { metricsOverview, MetricsAuthError } from "@/lib/api";
import type { MetricsOverview } from "@/lib/types";
import styles from "./metrics.module.css";

const TOKEN_KEY = "playloom_metrics_token";

export default function MetricsPage() {
  const [data, setData] = useState<MetricsOverview | null>(null);
  const [needToken, setNeedToken] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tokenInput, setTokenInput] = useState("");

  // 첫 문장이 await 라 이펙트에서 호출해도 동기 setState 가 없다
  const load = useCallback(async (token: string) => {
    try {
      const res = await metricsOverview(token);
      setData(res);
      setNeedToken(false);
    } catch (e) {
      if (e instanceof MetricsAuthError) {
        try { localStorage.removeItem(TOKEN_KEY); } catch {}
        setNeedToken(true);
        setError("토큰이 올바르지 않습니다");
      } else {
        setError("지표를 불러오지 못했습니다");
      }
    } finally {
      setLoading(false);
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
      await load(saved);
    })();
  }, [load]);

  function submitToken(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const token = tokenInput.trim();
    if (!token) return;
    try { localStorage.setItem(TOKEN_KEY, token); } catch {}
    setNeedToken(false);
    setLoading(true);
    setError(null);
    load(token);
  }

  if (needToken) {
    return (
      <main className={styles.page}>
        <h1 className={styles.title}>지표</h1>
        <form className={styles.tokenForm} onSubmit={submitToken}>
          <input
            className={styles.tokenInput}
            type="password"
            value={tokenInput}
            onChange={(e) => setTokenInput(e.target.value)}
            placeholder="접근 토큰"
            aria-label="접근 토큰"
          />
          <button className={styles.tokenButton} type="submit">확인</button>
        </form>
        {error && <p className={styles.error}>{error}</p>}
      </main>
    );
  }

  if (loading) {
    return <main className={styles.page}><p>불러오는 중…</p></main>;
  }

  if (error || !data) {
    return <main className={styles.page}><p className={styles.error}>{error ?? "데이터가 없습니다"}</p></main>;
  }

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>지표</h1>

      <section className={styles.summary}>
        <div className={styles.card}>
          <div className={styles.cardLabel}>총 노출수</div>
          <div className={styles.cardValue}>{data.totalImpressions.toLocaleString()}</div>
        </div>
      </section>

      <section className={styles.block}>
        <h2 className={styles.blockTitle}>경로별</h2>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>경로</th><th>노출</th><th>UP</th><th>DOWN</th><th>CLICK</th><th>반응률</th>
              </tr>
            </thead>
            <tbody>
              {data.contexts.map((c) => (
                <tr key={c.context}>
                  <td>{c.context}</td>
                  <td>{c.impressions.toLocaleString()}</td>
                  <td>{c.up.toLocaleString()}</td>
                  <td>{c.down.toLocaleString()}</td>
                  <td>{c.click.toLocaleString()}</td>
                  <td>{(c.upRate * 100).toFixed(2)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className={styles.block}>
        <h2 className={styles.blockTitle}>노출 상위 게임</h2>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr><th>순위</th><th>게임</th><th>노출</th></tr>
            </thead>
            <tbody>
              {data.topGames.map((g, i) => (
                <tr key={g.appid}>
                  <td>{i + 1}</td>
                  <td>{g.name ?? `appid ${g.appid}`}</td>
                  <td>{g.impressions.toLocaleString()}</td>
                </tr>
              ))}
              {data.topGames.length === 0 && (
                <tr><td colSpan={3}>아직 노출 데이터가 없습니다</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}
