"use client";

import { useEffect, useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { useT } from "@/lib/i18n/useT";
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
  const t = useT();
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
  // 매 조회마다 증가하는 요청 번호, 응답이 왔을 때 이 번호가 최신이 아니면(더 나중 요청,선택,닫기로 무효화됨) 결과를 버림
  const searchRequestIdRef = useRef(0);
  // 디바운스 중인(아직 발화 전) setTimeout id, gameName 이 안 바뀌는 close 경로(바깥 클릭 등)에서도 확실히 취소하려고 별도로 들고 있음
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 지금까지 나간 요청을 전부 낡은 것으로 만듦, 늦게 도착하는 응답이 있어도 setSuggestions 를 못 하게 막고
  // 아직 발화 전인 디바운스 타이머도 여기서 같이 취소함(gameName 이 안 바뀌는 close 경로에서 타이머가 살아남는 걸 막음)
  function invalidatePendingSearch() {
    searchRequestIdRef.current += 1;
    if (debounceTimerRef.current !== null) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
  }

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
      debounceTimerRef.current = null;
      // 이 조회 하나만의 번호를 찍어두고, 응답이 왔을 때 그사이 더 최신 요청,선택,닫기가 없었는지 확인함
      const requestId = (searchRequestIdRef.current += 1);
      gamesSearch(query)
        .then((items) => {
          if (searchRequestIdRef.current === requestId) {
            setSuggestions(items);
          }
        })
        .catch(() => {
          if (searchRequestIdRef.current === requestId) {
            setSuggestions([]);
          }
        });
    }, AUTOCOMPLETE_DEBOUNCE_MS);
    debounceTimerRef.current = timer;

    return () => {
      clearTimeout(timer);
      if (debounceTimerRef.current === timer) {
        debounceTimerRef.current = null;
      }
    };
  }, [gameName]);

  // 자동완성 바깥을 클릭하면 목록을 닫음, 닫은 뒤엔 대기 중이던 타이머,늦게 오는 응답 모두 무시함
  useEffect(() => {
    function handleOutsideClick(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        invalidatePendingSearch();
        setSuggestions([]);
      }
    }
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, []);

  function selectSuggestion(name: string) {
    skipNextSearchRef.current = true;
    invalidatePendingSearch();
    setGameName(name);
    setSuggestions([]);
  }

  // 글자 수가 기준(2자) 밑으로 내려가면 자동완성 목록을 바로 비움, 기준 이상이면 effect 가 디바운스 조회를 맡음
  function handleGameNameChange(value: string) {
    setGameName(value);
    if (value.trim().length < AUTOCOMPLETE_MIN_LENGTH) {
      invalidatePendingSearch();
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
          <h3>{t("similar.overlayTitle")}</h3>
          <p>{t("common.pleaseWait")}</p>
        </div>
      ) : null}

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>{t("similar.title")}</h1>
          <p className={styles.subtitle}>{t("similar.subtitle")}</p>
        </section>

        <div className={styles.layout}>
          <div className={styles.leftPanel}>
            <GlassPanel as="div" className={styles.panel}>
              <h2 className={styles.panelTitle}>{t("similar.panelTitle")}</h2>

              <div className={styles.warning}>{t("similar.warning")}</div>

              <div ref={wrapperRef} className={styles.searchWrapper}>
                <input
                  ref={inputRef}
                  type="text"
                  className={`${styles.textInput} ${invalid ? styles.invalid : ""}`}
                  placeholder={t("similar.placeholder")}
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
                <label htmlFor="similar-review">{t("common.minReviews")}</label>
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
                <label htmlFor="similar-korean">{t("common.koreanOnly")}</label>
                <input
                  type="checkbox"
                  id="similar-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="similar-free">{t("common.freeOnly")}</label>
                <input
                  type="checkbox"
                  id="similar-free"
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
            <h2 className={styles.resultTitle}>{t("similar.resultTitle")}</h2>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>{t("common.errorTitle")}</h3>
                <p>{t("similar.errorHint")}</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>{t("common.rateTitle")}</h3>
                <p>{t("common.rateHint")}</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>{t("common.noGames")}</h3>
                <p>{t("similar.noGamesHint")}</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                usedTagsLabel={t("similar.usedTagsLabel")}
                games={games}
                reviews={result.review}
                context="SIMILAR"
                reviewOptions={REVIEW_OPTIONS}
                onRecommendAgain={handleRecommendAgain}
                recommendAgainDisabled={loading}
              />
            ) : (
              <div className={styles.notice}>
                <h3>{t("similar.ready")}</h3>
                <p>{t("similar.readyHint")}</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
