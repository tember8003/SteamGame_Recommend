"use client";

import { useEffect, useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { gamesSearch, recommendSimilar } from "@/lib/api";
import { mergeShownAppids, toExcludeAppids } from "@/lib/reroll";
import type { GameSearchResult, RecommendationResult } from "@/lib/types";
import styles from "./page.module.css";

// similar.html 의 리뷰 섹션 구성(제목 없음, 시간 통계 있음, 추천율 막대 있음, 원문 없음)
const REVIEW_OPTIONS: ReviewDisplayOptions = {
  showHeader: false,
  showHours: true,
  showRatingBar: true,
  showReviewText: false,
};

const AUTOCOMPLETE_DEBOUNCE_MS = 280;
const AUTOCOMPLETE_MIN_LENGTH = 2;

type Status = "idle" | "loading" | "error" | "rateLimited";

function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// 원본 similar.html(게임 이름 하나를 검색해 비슷한 태그의 게임을 추천받는 화면)을 /steam/similar 라우트로 옮김
export default function SimilarRecommendPage() {
  const [gameName, setGameName] = useState("");
  const [suggestions, setSuggestions] = useState<GameSearchResult[]>([]);
  const [review, setReview] = useState(1000);
  const [koreanOnly, setKoreanOnly] = useState(false);
  const [freeOnly, setFreeOnly] = useState(false);

  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [shownAppids, setShownAppids] = useState<number[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [invalid, setInvalid] = useState(false);

  const inputRef = useRef<HTMLInputElement>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  // 자동완성 목록에서 항목을 고른 직후엔 같은 입력값으로 검색을 다시 태우지 않도록 막는 표시
  const skipNextSearchRef = useRef(false);

  // 입력값이 바뀔 때마다 디바운스 뒤 gamesSearch 로 자동완성 후보를 가져옴
  // 목록을 비우는 동기 처리는 이벤트 핸들러(handleGameNameChange, selectSuggestion)가 맡고
  // 이 effect 는 비동기 조회 예약,취소만 다룸
  useEffect(() => {
    if (skipNextSearchRef.current) {
      skipNextSearchRef.current = false;
      return;
    }

    const query = gameName.trim();
    if (query.length < AUTOCOMPLETE_MIN_LENGTH) {
      return;
    }

    const timer = setTimeout(() => {
      gamesSearch(query)
        .then(setSuggestions)
        .catch(() => setSuggestions([]));
    }, AUTOCOMPLETE_DEBOUNCE_MS);

    return () => clearTimeout(timer);
  }, [gameName]);

  // 자동완성 바깥을 클릭하면 목록을 닫음
  useEffect(() => {
    function handleOutsideClick(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setSuggestions([]);
      }
    }
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, []);

  function selectSuggestion(name: string) {
    skipNextSearchRef.current = true;
    setGameName(name);
    setSuggestions([]);
  }

  // 글자 수가 기준(2자) 밑으로 내려가면 자동완성 목록을 바로 비움, 기준 이상이면 effect 가 디바운스 조회를 맡음
  function handleGameNameChange(value: string) {
    setGameName(value);
    if (value.trim().length < AUTOCOMPLETE_MIN_LENGTH) {
      setSuggestions([]);
    }
  }

  async function runRecommend(isFresh: boolean) {
    const trimmed = gameName.trim();
    if (!trimmed) {
      setInvalid(true);
      inputRef.current?.focus();
      setTimeout(() => setInvalid(false), 2000);
      return;
    }

    setStatus("loading");
    try {
      const data = await recommendSimilar(trimmed, {
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
          <h3>Finding similar games...</h3>
          <p>Please wait a moment</p>
        </div>
      ) : null}

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>Similar Game Finder</h1>
          <p className={styles.subtitle}>Discover games with similar tags to your favorites</p>
        </section>

        <div className={styles.layout}>
          <div className={styles.leftPanel}>
            <GlassPanel as="div" className={styles.panel}>
              <h2 className={styles.panelTitle}>Game Search</h2>

              <div className={styles.warning}>Enter a game name and select from the autocomplete list</div>

              <div ref={wrapperRef} className={styles.searchWrapper}>
                <input
                  ref={inputRef}
                  type="text"
                  className={`${styles.textInput} ${invalid ? styles.invalid : ""}`}
                  placeholder="Enter game name for recommendations"
                  autoComplete="off"
                  value={gameName}
                  onChange={(e) => handleGameNameChange(e.target.value)}
                  onKeyDown={handleKeyDown}
                />
                {suggestions.length > 0 ? (
                  <div className={styles.autocompleteList}>
                    {suggestions.map((item) => (
                      <button
                        type="button"
                        key={item.gameName}
                        className={styles.autocompleteItem}
                        onClick={() => selectSuggestion(item.gameName)}
                      >
                        {item.headerImage ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img src={item.headerImage} alt="" className={styles.autocompleteThumb} />
                        ) : null}
                        <span>{item.gameName}</span>
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            </GlassPanel>

            <GlassPanel as="div" className={styles.controlsPanel}>
              <div className={styles.controlItem}>
                <label htmlFor="similar-review">Min Reviews</label>
                <input
                  type="number"
                  id="similar-review"
                  min={0}
                  max={100000}
                  value={review}
                  onChange={(e) => setReview(Number(e.target.value))}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="similar-korean">Korean Only</label>
                <input
                  type="checkbox"
                  id="similar-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="similar-free">Free Only</label>
                <input
                  type="checkbox"
                  id="similar-free"
                  checked={freeOnly}
                  onChange={(e) => setFreeOnly(e.target.checked)}
                />
              </div>

              <button type="button" className={styles.submitBtn} onClick={handleSubmit} disabled={loading}>
                {loading ? <span className={styles.spinner} aria-hidden="true" /> : null}
                Get Recommendations
              </button>
            </GlassPanel>
          </div>

          <GlassPanel as="section" className={styles.resultPanel}>
            <h2 className={styles.resultTitle}>Similar Games</h2>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>An error occurred</h3>
                <p>Please check the game name or try again later.</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>Too many requests</h3>
                <p>Please wait a moment before trying again.</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>No games found</h3>
                <p>Please check the game name or try a different game!</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                usedTagsLabel="Analyzed Tags"
                games={games}
                reviews={result.review}
                context="SIMILAR"
                reviewOptions={REVIEW_OPTIONS}
                onRecommendAgain={handleRecommendAgain}
                recommendAgainDisabled={loading}
              />
            ) : (
              <div className={styles.notice}>
                <h3>Ready to find similar games</h3>
                <p>Search for a game to get recommendations based on similar tags and features!</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
