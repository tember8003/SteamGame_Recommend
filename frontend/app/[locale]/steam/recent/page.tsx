"use client";

import { useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { useT } from "@/lib/i18n/useT";
import { recommendRecent } from "@/lib/api";
import { mergeShownAppids, toExcludeAppids } from "@/lib/reroll";
import type { RecommendationResult } from "@/lib/types";
import styles from "./page.module.css";

// recent.html 의 리뷰 섹션 구성(제목 있음, 시간 통계 있음, 추천율 막대 있음, 원문 있음)
const REVIEW_OPTIONS: ReviewDisplayOptions = {
  showHeader: true,
  showHours: true,
  showRatingBar: true,
  showReviewText: true,
};

type Status = "idle" | "loading" | "error" | "rateLimited";

// 429 응답은 lib/api.ts 가 `POST ... failed: 429` 형태의 메시지로 던지므로 여기서 상태코드만 골라냄
function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// 원본 recent.html(최근 2주 플레이 기록을 분석해 추천을 받는 화면)을 /steam/recent 라우트로 옮김
export default function RecentRecommendPage() {
  const t = useT();
  const [steamId, setSteamId] = useState("");
  const [review, setReview] = useState(1000);
  const [koreanOnly, setKoreanOnly] = useState(false);
  const [freeOnly, setFreeOnly] = useState(false);

  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [shownAppids, setShownAppids] = useState<number[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [invalid, setInvalid] = useState(false);
  // 결과를 만든 실제 조회에 쓰인 SteamID, 입력창을 나중에 고쳐도 피드백은 그 결과를 만든 값을 따라가야 함
  const [queriedSteamId, setQueriedSteamId] = useState("");

  const steamIdRef = useRef<HTMLInputElement>(null);

  // isFresh 가 true 면 새 검색(이전에 나온 게임을 제외하지 않음), false 면 다시추천(누적된 appid 를 제외함)
  async function runRecommend(isFresh: boolean) {
    const trimmed = steamId.trim();
    if (!trimmed) {
      setInvalid(true);
      steamIdRef.current?.focus();
      setTimeout(() => setInvalid(false), 2000);
      return;
    }

    setStatus("loading");
    try {
      const data = await recommendRecent(trimmed, {
        review,
        koreanCheck: koreanOnly,
        freeCheck: freeOnly,
        excludeAppids: isFresh ? undefined : toExcludeAppids(shownAppids),
      });
      setResult(data);
      setShownAppids((prev) => mergeShownAppids(isFresh ? [] : prev, data.recommendedGame));
      setQueriedSteamId(trimmed);
      setStatus("idle");
    } catch (error) {
      setResult(null);
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  function handleSubmit() {
    void runRecommend(true);
  }

  function handleRecommendAgain() {
    void runRecommend(false);
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter") {
      handleSubmit();
    }
  }

  const loading = status === "loading";
  const games = result?.recommendedGame ?? [];

  return (
    <div className={styles.page}>
      <Header />

      {loading ? (
        <div className={styles.overlay}>
          <div className={styles.overlaySpinner} aria-hidden="true" />
          <h3>{t("recent.overlayTitle")}</h3>
          <p>{t("common.pleaseWait")}</p>
        </div>
      ) : null}

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>{t("recent.title")}</h1>
          <p className={styles.subtitle}>{t("recent.subtitle")}</p>
        </section>

        <div className={styles.layout}>
          <div className={styles.leftPanel}>
            <GlassPanel as="div" className={styles.panel}>
              <h2 className={styles.panelTitle}>{t("recent.panelTitle")}</h2>

              <div className={styles.warning}>{t("recent.warning")}</div>

              <input
                type="text"
                ref={steamIdRef}
                className={`${styles.textInput} ${invalid ? styles.invalid : ""}`}
                placeholder={t("recent.placeholder")}
                aria-label={t("common.steamId")}
                value={steamId}
                onChange={(e) => setSteamId(e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </GlassPanel>

            <GlassPanel as="div" className={styles.controlsPanel}>
              <div className={styles.controlItem}>
                <label htmlFor="recent-review">{t("common.minReviews")}</label>
                <input
                  type="number"
                  id="recent-review"
                  min={0}
                  max={100000}
                  value={review}
                  onChange={(e) => setReview(Number(e.target.value))}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="recent-korean">{t("common.koreanOnly")}</label>
                <input
                  type="checkbox"
                  id="recent-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="recent-free">{t("common.freeOnly")}</label>
                <input
                  type="checkbox"
                  id="recent-free"
                  checked={freeOnly}
                  onChange={(e) => setFreeOnly(e.target.checked)}
                />
              </div>

              <button type="button" className={styles.submitBtn} onClick={handleSubmit} disabled={loading}>
                {loading ? <span className={styles.spinner} aria-hidden="true" /> : null}
                {t("common.getRecommendations")}
              </button>
            </GlassPanel>
          </div>

          <GlassPanel as="section" className={styles.resultPanel}>
            <h2 className={styles.resultTitle}>{t("recent.resultTitle")}</h2>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>{t("common.errorTitle")}</h3>
                <p>{t("recent.errorHint")}</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>{t("common.rateTitle")}</h3>
                <p>{t("common.rateHint")}</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>{t("common.noGames")}</h3>
                <p>{t("recent.noGamesHint")}</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                usedTagsLabel={t("recent.usedTagsLabel")}
                games={games}
                reviews={result.review}
                context="RECENT"
                steamId={queriedSteamId}
                reviewOptions={REVIEW_OPTIONS}
                onRecommendAgain={handleRecommendAgain}
                recommendAgainDisabled={loading}
              />
            ) : (
              <div className={styles.notice}>
                <h3>{t("recent.ready")}</h3>
                <p>{t("recent.readyHint")}</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
