"use client";

import { useEffect, useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import GameCard from "@/components/GameCard";
import { useT } from "@/lib/i18n/useT";
import { choiceResult, choiceSelect, choiceStart } from "@/lib/api";
import { roundProgress } from "@/lib/miniGame";
import type { ChoiceResult, ChoiceStartResponse, RecommendationResult } from "@/lib/types";
import styles from "./page.module.css";

type Phase = "welcome" | "playing" | "afterRounds";
type Status = "idle" | "busy" | "error" | "rateLimited";
type OptionKey = "game1" | "game2";

// 429 응답은 lib/api.ts 가 `POST ... failed: 429` 형태의 메시지로 던지므로 여기서 상태코드만 골라냄
function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// 리뷰 긍정률로 배지 톤을 고름, 80 이상은 기본색,60~79 는 mixed,그 밑은 negative 로 나눔
function reviewScoreTone(score: number | undefined): "mixed" | "negative" | null {
  if (!score) return null;
  if (score >= 80) return null;
  if (score >= 60) return "mixed";
  return "negative";
}

function GamepadIcon() {
  return (
    <svg className={styles.bigIcon} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4" aria-hidden="true">
      <rect x="2" y="7" width="20" height="10" rx="5" />
      <path d="M7 10v4M5 12h4M15.4 11h.01M18.4 13h.01" strokeLinecap="round" />
    </svg>
  );
}

function SparkleIcon() {
  return (
    <svg className={styles.bigIcon} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12 2l1.9 5.5L19.4 9l-5.5 1.9L12 16.4l-1.9-5.5L4.6 9l5.5-1.9L12 2z" />
    </svg>
  );
}

// game1,game2 후보 카드 한 장, 클릭하면 onSelect 로 appid 를 넘김
function GameOption({
  data,
  selected,
  disabled,
  onSelect,
}: {
  data: RecommendationResult;
  selected: boolean;
  disabled: boolean;
  onSelect: (gameId: number) => void;
}) {
  const t = useT();
  const game = data.recommendedGame[0];
  if (!game) return null;

  const review = data.review[0];
  const score = review?.positiveRating;
  const tone = reviewScoreTone(score);
  const scoreCls = [styles.reviewScore, tone ? styles[tone] : ""].filter(Boolean).join(" ");
  const cls = [styles.gameOption, selected ? styles.gameOptionSelected : ""].filter(Boolean).join(" ");

  return (
    <button type="button" className={cls} disabled={disabled} onClick={() => onSelect(game.appid)}>
      {game.headerImage ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img src={game.headerImage} alt={game.name} className={styles.gameImage} />
      ) : null}
      <div className={styles.gameTitle}>{game.name}</div>
      <p className={styles.gameDescription}>{game.shortDescription || t("choice.noDescription")}</p>
      {data.usedTags.length > 0 ? (
        <div className={styles.gameTags}>
          {data.usedTags.slice(0, 3).map((tag) => (
            <span key={tag} className={styles.gameTag}>
              {tag}
            </span>
          ))}
        </div>
      ) : null}
      <div className={styles.gameReview}>
        {review ? (
          <>
            <span className={scoreCls}>{score ? `${Math.round(score)}%` : "N/A"}</span>
            <span className={styles.reviewSummary}>{review.reviewSummary || t("choice.noRating")}</span>
          </>
        ) : (
          <span className={styles.reviewScore}>{t("choice.noReviewData")}</span>
        )}
      </div>
    </button>
  );
}

function Notice({ status }: { status: Status }) {
  const t = useT();
  if (status === "error") {
    return (
      <div className={styles.notice}>
        <p>{t("game.errorNotice")}</p>
      </div>
    );
  }
  if (status === "rateLimited") {
    return (
      <div className={styles.notice}>
        <p>{t("game.rateNotice")}</p>
      </div>
    );
  }
  return null;
}

// 원본 choice.html(두 게임 중 마음에 드는 쪽을 골라 취향 태그, 최종 추천을 받는 미니게임)을 /steam/choice 라우트로 옮김
export default function ChoicePage() {
  const t = useT();
  const [phase, setPhase] = useState<Phase>("welcome");
  const [status, setStatus] = useState<Status>("idle");
  const [session, setSession] = useState<ChoiceStartResponse | null>(null);
  const [currentRoundIndex, setCurrentRoundIndex] = useState(0);
  const [selectedOption, setSelectedOption] = useState<OptionKey | null>(null);
  const [discoveredTags, setDiscoveredTags] = useState<string[]>([]);
  const [reviewMin, setReviewMin] = useState(1000);
  const [koreanOnly, setKoreanOnly] = useState(false);
  const [freeOnly, setFreeOnly] = useState(false);
  const [result, setResult] = useState<ChoiceResult | null>(null);

  const busy = status === "busy";
  const totalRounds = session?.rounds.length ?? 0;
  const currentRound = session?.rounds[currentRoundIndex];
  const progress = roundProgress(currentRoundIndex, totalRounds);
  const progressLabel =
    progress.totalRounds > 0
      ? t("game.roundOf", { n: progress.roundNumber, total: progress.totalRounds })
      : t("game.round", { n: progress.roundNumber });

  // 언마운트 여부, 연출 지연 도중 페이지를 벗어나도 그 뒤 응답으로 state 를 건드리지 않도록 막는 가드로 씀
  const mountedRef = useRef(true);
  useEffect(() => {
    return () => {
      mountedRef.current = false;
    };
  }, []);

  async function handleStart() {
    setStatus("busy");
    try {
      const data = await choiceStart();
      if (!mountedRef.current) return;
      setSession(data);
      setCurrentRoundIndex(0);
      setSelectedOption(null);
      setPhase("playing");
      setStatus("idle");
    } catch (error) {
      if (!mountedRef.current) return;
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  async function handleSelect(option: OptionKey, gameId: number) {
    if (!session || busy) return;
    setSelectedOption(option);
    setStatus("busy");
    try {
      // 고른 카드를 잠깐 강조해서 보여주는 연출 지연, 게임성상 의미가 있어 유지함
      await new Promise((resolve) => setTimeout(resolve, 500));
      if (!mountedRef.current) return;
      const selection = await choiceSelect(session.sessionId, currentRoundIndex, gameId);
      if (!mountedRef.current) return;

      if (selection.completed) {
        // 마지막 라운드 직후 기본 필터(리뷰 1000, 한국어,무료 조건 없음)로 취향 태그만 먼저 뽑아 보여줌
        const preferences = await choiceResult(session.sessionId, 1000, null, null);
        if (!mountedRef.current) return;
        setDiscoveredTags(preferences.usedTags);
        setPhase("afterRounds");
        setStatus("idle");
      } else {
        setCurrentRoundIndex(selection.nextRoundIndex);
        setSelectedOption(null);
        setStatus("idle");
      }
    } catch (error) {
      if (!mountedRef.current) return;
      setSelectedOption(null);
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  async function handleGetRecommendations() {
    if (!session) return;
    setStatus("busy");
    try {
      const data = await choiceResult(session.sessionId, reviewMin, koreanOnly, freeOnly);
      if (!mountedRef.current) return;
      setResult(data);
      setStatus("idle");
    } catch (error) {
      if (!mountedRef.current) return;
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>{t("choice.title")}</h1>
          <p className={styles.subtitle}>{t("choice.subtitle")}</p>
        </section>

        <div className={styles.layout}>
          <aside className={styles.leftPanel}>
            {phase === "welcome" ? (
              <GlassPanel as="div" className={styles.panel}>
                <h2 className={styles.panelTitle}>{t("choice.panelTitle")}</h2>
                <p className={styles.description}>{t("choice.description")}</p>
                <div className={styles.note}>{t("choice.note")}</div>
                <button type="button" className={styles.primaryBtn} onClick={handleStart} disabled={busy}>
                  {busy ? <span className={styles.spinner} aria-hidden="true" /> : null}
                  {busy ? t("choice.starting") : t("choice.start")}
                </button>
                <Notice status={status} />
              </GlassPanel>
            ) : null}

            {phase === "playing" ? (
              <GlassPanel as="div" className={styles.panel}>
                <h2 className={styles.panelTitle}>{t("choice.progress")}</h2>
                <div className={styles.progressBar}>
                  <div className={styles.progressFill} style={{ width: `${progress.percent}%` }} />
                </div>
                <div className={styles.progressText}>{progressLabel}</div>
              </GlassPanel>
            ) : null}

            {phase === "afterRounds" ? (
              <GlassPanel as="div" className={styles.panel}>
                <h2 className={styles.panelTitle}>{t("choice.preferences")}</h2>
                <div className={styles.tagsDisplay}>
                  {discoveredTags.length > 0 ? (
                    discoveredTags.map((tag) => (
                      <span key={tag} className={styles.tag}>
                        {tag}
                      </span>
                    ))
                  ) : (
                    <p className={styles.emptyTags}>{t("choice.emptyTags")}</p>
                  )}
                </div>

                <h3 className={styles.panelTitle}>{t("choice.customizeFilters")}</h3>
                <div className={styles.controlsGroup}>
                  <div className={styles.controlItem}>
                    <label htmlFor="choice-review">{t("common.minReviews")}</label>
                    <input
                      type="number"
                      id="choice-review"
                      min={0}
                      max={1000000}
                      value={reviewMin}
                      onChange={(e) => setReviewMin(Number(e.target.value))}
                    />
                  </div>
                  <div className={styles.controlItem}>
                    <label htmlFor="choice-korean">{t("common.koreanOnly")}</label>
                    <input
                      type="checkbox"
                      id="choice-korean"
                      checked={koreanOnly}
                      onChange={(e) => setKoreanOnly(e.target.checked)}
                    />
                  </div>
                  <div className={styles.controlItem}>
                    <label htmlFor="choice-free">{t("common.freeOnly")}</label>
                    <input
                      type="checkbox"
                      id="choice-free"
                      checked={freeOnly}
                      onChange={(e) => setFreeOnly(e.target.checked)}
                    />
                  </div>
                </div>

                <button type="button" className={styles.primaryBtn} onClick={handleGetRecommendations} disabled={busy}>
                  {busy ? <span className={styles.spinner} aria-hidden="true" /> : null}
                  {t("common.getRecommendations")}
                </button>
                <Notice status={status} />
              </GlassPanel>
            ) : null}
          </aside>

          <GlassPanel as="section" className={styles.gameArea}>
            {phase === "welcome" ? (
              <div className={styles.welcome}>
                <GamepadIcon />
                <h2>{t("choice.welcomeTitle")}</h2>
                <p>{t("choice.welcomeText")}</p>
              </div>
            ) : null}

            {phase === "playing" && currentRound ? (
              <div className={styles.roundArea}>
                <div className={styles.roundInfo}>
                  <div className={styles.roundTitle}>{t("game.round", { n: currentRoundIndex + 1 })}</div>
                  <div className={styles.roundSubtitle}>{t("choice.chooseGame")}</div>
                </div>

                <Notice status={status} />

                <div className={styles.vsContainer}>
                  <GameOption
                    data={currentRound.game1}
                    selected={selectedOption === "game1"}
                    disabled={busy}
                    onSelect={(gameId) => handleSelect("game1", gameId)}
                  />
                  <div className={styles.vsDivider}>VS</div>
                  <GameOption
                    data={currentRound.game2}
                    selected={selectedOption === "game2"}
                    disabled={busy}
                    onSelect={(gameId) => handleSelect("game2", gameId)}
                  />
                </div>
              </div>
            ) : null}

            {phase === "afterRounds" ? (
              busy ? (
                <div className={styles.loadingArea}>
                  <span className={styles.bigSpinner} aria-hidden="true" />
                  <p>{t("choice.analyzing")}</p>
                </div>
              ) : result ? (
                <div className={styles.resultsArea}>
                  <div className={styles.resultsHeader}>
                    <h2>{t("choice.resultsTitle")}</h2>
                    <p>{t("choice.resultsSubtitle", { n: result.recommendedGames.length })}</p>
                  </div>
                  {result.recommendedGames.length > 0 ? (
                    <div className={styles.grid}>
                      {result.recommendedGames.map((game) => (
                        <GameCard key={game.appid} game={game} />
                      ))}
                    </div>
                  ) : (
                    <div className={styles.notice}>
                      <p>{t("choice.noMatches")}</p>
                    </div>
                  )}
                </div>
              ) : (
                <div className={styles.welcome}>
                  <SparkleIcon />
                  <h2>{t("choice.discoveredTitle")}</h2>
                  <p>{t("choice.discoveredText")}</p>
                </div>
              )
            ) : null}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
