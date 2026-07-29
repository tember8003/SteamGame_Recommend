"use client";

import { useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { recommendInput } from "@/lib/api";
import { mergeShownAppids, toExcludeAppids } from "@/lib/reroll";
import type { RecommendationResult } from "@/lib/types";
import styles from "./page.module.css";

// input.html 의 리뷰 섹션 구성(제목 있음, 시간 통계 없음, 추천율 막대 있음, 원문 없음)
const REVIEW_OPTIONS: ReviewDisplayOptions = {
  showHeader: true,
  showHours: false,
  showRatingBar: true,
  showReviewText: false,
};

const MIN_TEXT_LENGTH = 3;

type Status = "idle" | "loading" | "error" | "rateLimited";

// 429 응답은 lib/api.ts 가 `POST ... failed: 429` 형태의 메시지로 던지므로 여기서 상태코드만 골라냄
function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// 원본 input.html(텍스트로 게임 취향을 설명하면 추천을 받는 화면)을 /steam/input 라우트로 옮김
export default function InputRecommendPage() {
  const [text, setText] = useState("");
  const [review, setReview] = useState(1000);
  const [koreanOnly, setKoreanOnly] = useState(false);
  const [freeOnly, setFreeOnly] = useState(false);

  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [shownAppids, setShownAppids] = useState<number[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [invalid, setInvalid] = useState(false);

  const textRef = useRef<HTMLTextAreaElement>(null);

  // isFresh 가 true 면 새 검색(이전에 나온 게임을 제외하지 않음), false 면 다시추천(누적된 appid 를 제외함)
  async function runRecommend(isFresh: boolean) {
    const trimmed = text.trim();
    if (trimmed.length < MIN_TEXT_LENGTH) {
      setInvalid(true);
      textRef.current?.focus();
      setTimeout(() => setInvalid(false), 2000);
      return;
    }

    setStatus("loading");
    try {
      const data = await recommendInput(trimmed, {
        review,
        koreanCheck: koreanOnly,
        freeCheck: freeOnly,
        excludeAppids: isFresh ? undefined : toExcludeAppids(shownAppids),
      });
      setResult(data);
      setShownAppids((prev) => mergeShownAppids(isFresh ? [] : prev, data.recommendedGame));
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

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && e.ctrlKey) {
      e.preventDefault();
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
          <h1 className={styles.title}>Find Game Tags with Gemini</h1>
          <p className={styles.subtitle}>Describe your desired game features and find the perfect game!</p>
        </section>

        <div className={styles.layout}>
          <GlassPanel as="aside" className={styles.panel}>
            <h2 className={styles.panelTitle}>Custom Input</h2>

            <div className={styles.warning}>Must enter at least 3 characters to work!</div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="input-text">
                Game Features
              </label>
              <textarea
                id="input-text"
                ref={textRef}
                className={`${styles.textarea} ${invalid ? styles.invalid : ""}`}
                placeholder="Enter your desired game features (e.g., 'fantasy RPG with magic system')"
                value={text}
                onChange={(e) => setText(e.target.value)}
                onKeyDown={handleKeyDown}
              />
            </div>

            <div className={styles.controls}>
              <h3 className={styles.controlsTitle}>Options</h3>
              <div className={styles.controlItem}>
                <label htmlFor="input-review">Review Count</label>
                <input
                  type="number"
                  id="input-review"
                  min={0}
                  max={100000}
                  value={review}
                  onChange={(e) => setReview(Number(e.target.value))}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="input-korean">Korean Support Only</label>
                <input
                  type="checkbox"
                  id="input-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="input-free">Free Games Only</label>
                <input
                  type="checkbox"
                  id="input-free"
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
              <h2 className={styles.resultTitle}>Recommended Games</h2>
              <p className={styles.resultSubtitle}>AI-powered game recommendations based on your preferences</p>
            </div>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>An error occurred</h3>
                <p>Please try again later.</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>Too many requests</h3>
                <p>Please wait a moment before trying again.</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>No games found</h3>
                <p>Try different keywords!</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                games={games}
                reviews={result.review}
                context="INPUT"
                reviewOptions={REVIEW_OPTIONS}
                onRecommendAgain={handleRecommendAgain}
                recommendAgainDisabled={loading}
              />
            ) : (
              <div className={styles.notice}>
                <h3>Ready to Find Games</h3>
                <p>Enter your game preferences and click &quot;Get Recommendations&quot; to start!</p>
                <p>Please configure your Gemini API key to use this feature.</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
