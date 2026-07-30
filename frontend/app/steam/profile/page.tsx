"use client";

import { useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { recommendProfile } from "@/lib/api";
import { mergeShownAppids, toExcludeAppids } from "@/lib/reroll";
import type { RecommendationResult } from "@/lib/types";
import styles from "./page.module.css";

// profile.html 의 리뷰 섹션 구성(제목 있음, 시간 통계 없음, 추천율 막대 있음, 원문 없음)
const REVIEW_OPTIONS: ReviewDisplayOptions = {
  showHeader: true,
  showHours: false,
  showRatingBar: true,
  showReviewText: false,
};

type Status = "idle" | "loading" | "error" | "rateLimited";

// 429 응답은 lib/api.ts 가 `POST ... failed: 429` 형태의 메시지로 던지므로 여기서 상태코드만 골라냄
function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// 원본 profile.html(Steam 프로필을 분석해 추천을 받는 화면)을 /steam/profile 라우트로 옮김
export default function ProfileRecommendPage() {
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
      const data = await recommendProfile(trimmed, {
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

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>Steam Profile-Based Recommendation</h1>
          <p className={styles.subtitle}>Analyze your Steam profile to find games matching your playing style</p>
        </section>

        <div className={styles.layout}>
          <GlassPanel as="aside" className={styles.panel}>
            <h2 className={styles.panelTitle}>Profile Analysis</h2>

            <div className={styles.warning}>
              Please enter your Steam ID accurately. Only public profiles can be analyzed.
            </div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="profile-id">
                Steam ID
              </label>
              <input
                type="text"
                id="profile-id"
                ref={steamIdRef}
                className={`${styles.textInput} ${invalid ? styles.invalid : ""}`}
                placeholder="Enter your Steam ID (e.g., your_username)"
                value={steamId}
                onChange={(e) => setSteamId(e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </div>

            <div className={styles.controls}>
              <h3 className={styles.controlsTitle}>Options</h3>
              <div className={styles.controlItem}>
                <label htmlFor="profile-review">Review Count</label>
                <input
                  type="number"
                  id="profile-review"
                  min={0}
                  max={100000}
                  value={review}
                  onChange={(e) => setReview(Number(e.target.value))}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="profile-korean">Korean Support Only</label>
                <input
                  type="checkbox"
                  id="profile-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="profile-free">Free Games Only</label>
                <input
                  type="checkbox"
                  id="profile-free"
                  checked={freeOnly}
                  onChange={(e) => setFreeOnly(e.target.checked)}
                />
              </div>
            </div>

            <button type="button" className={styles.submitBtn} onClick={handleSubmit} disabled={loading}>
              {loading ? <span className={styles.spinner} aria-hidden="true" /> : null}
              Get Recommendations
            </button>
          </GlassPanel>

          <GlassPanel as="section" className={styles.resultPanel}>
            <div className={styles.resultHeader}>
              <h2 className={styles.resultTitle}>Profile-Based Recommendations</h2>
              <p className={styles.resultSubtitle}>game recommendations based on your Steam profile analysis</p>
            </div>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>An error occurred</h3>
                <p>Please check your Steam ID or try again later.</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>Too many requests</h3>
                <p>Please wait a moment before trying again.</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>No games found</h3>
                <p>Please check your Steam ID or try different options!</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                usedTagsLabel="Tags Analyzed from Profile"
                games={games}
                reviews={result.review}
                context="PROFILE"
                steamId={queriedSteamId}
                reviewOptions={REVIEW_OPTIONS}
                onRecommendAgain={handleRecommendAgain}
                recommendAgainDisabled={loading}
              />
            ) : (
              <div className={styles.notice}>
                <h3>Ready to Analyze Your Profile</h3>
                <p>
                  Enter your Steam ID and click &quot;Get Recommendations&quot; to discover games perfectly matched
                  to your gaming style!
                </p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
