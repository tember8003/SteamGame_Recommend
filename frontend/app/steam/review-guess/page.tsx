"use client";

import { useEffect, useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import { reviewGuessAnswer, reviewGuessResult, reviewGuessStart } from "@/lib/api";
import { roundProgress } from "@/lib/miniGame";
import type {
  ReviewGuessAnswerResponse,
  ReviewGuessGame,
  ReviewGuessResult,
  ReviewGuessStartResponse,
} from "@/lib/types";
import styles from "./page.module.css";

type Phase = "start" | "playing" | "results";
type Status = "idle" | "busy" | "error" | "rateLimited";

// 429 응답은 lib/api.ts 가 `POST ... failed: 429` 형태의 메시지로 던지므로 여기서 상태코드만 골라냄
function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// 답변 결과가 나온 뒤 옵션 카드에 씌울 톤을 고름, 정답 카드는 항상 correct,내가 고른 오답만 incorrect 로 표시함
function optionTone(
  game: ReviewGuessGame,
  answer: ReviewGuessAnswerResponse | null,
  selectedGameId: number | null
): "correct" | "incorrect" | null {
  if (!answer) return null;
  const isCorrectOption =
    answer.correctAnswerId !== undefined ? game.appid === answer.correctAnswerId : game.name === answer.correctGameName;
  if (isCorrectOption) return "correct";
  if (selectedGameId === game.appid && !answer.correct) return "incorrect";
  return null;
}

// A~D 등급만 색을 입힘, S 등급은 원본과 동일하게 별도 색상 없이 기본색으로 남김
function gradeTone(grade: string): "gradeA" | "gradeB" | "gradeC" | "gradeD" | null {
  switch (grade) {
    case "A":
      return "gradeA";
    case "B":
      return "gradeB";
    case "C":
      return "gradeC";
    case "D":
      return "gradeD";
    default:
      return null;
  }
}

function CheckIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M8 12.5l2.5 2.5L16 9.5" />
    </svg>
  );
}

function XIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="9" />
      <path d="M9 9l6 6M15 9l-6 6" />
    </svg>
  );
}

// onRetry 를 주면 에러,요청 과다 문구 아래에 재시도 버튼을 같이 보여줌(결과 조회처럼 막다른 상태를 벗어날 수단이 따로 없는 경우에 씀)
function Notice({ status, onRetry }: { status: Status; onRetry?: () => void }) {
  if (status === "error" || status === "rateLimited") {
    return (
      <div className={styles.notice}>
        <p>
          {status === "rateLimited"
            ? "Too many requests. Please wait a moment before trying again."
            : "An error occurred. Please try again."}
        </p>
        {onRetry ? (
          <button type="button" className={styles.retryBtn} onClick={onRetry}>
            Try Again
          </button>
        ) : null}
      </div>
    );
  }
  return null;
}

// 원본 review-guess.html(리뷰 두 개를 읽고 어느 게임인지 맞히는 미니게임)을 /steam/review-guess 라우트로 옮김
export default function ReviewGuessPage() {
  const [phase, setPhase] = useState<Phase>("start");
  const [status, setStatus] = useState<Status>("idle");
  const [session, setSession] = useState<ReviewGuessStartResponse | null>(null);
  const [currentRoundIndex, setCurrentRoundIndex] = useState(0);
  const [selectedGameId, setSelectedGameId] = useState<number | null>(null);
  const [answer, setAnswer] = useState<ReviewGuessAnswerResponse | null>(null);
  // 라운드 시작 시점 점수 표시, 답변 제출 전에는 0 으로 두고 서버 응답을 받으면 갱신함
  const [score, setScore] = useState(0);
  const [result, setResult] = useState<ReviewGuessResult | null>(null);

  const busy = status === "busy";
  const answered = answer !== null;
  const totalRounds = session?.rounds.length ?? 0;
  const currentRound = session?.rounds[currentRoundIndex];
  const progress = roundProgress(currentRoundIndex, totalRounds);
  // 시작,답변 대기만 전체 화면 오버레이로 가림, 마지막 라운드 뒤 결과 조회는 원본처럼 오버레이 없이 조용히 진행함
  const showOverlay = busy && (phase === "start" || (phase === "playing" && !answered));

  // 언마운트 여부, 연출 지연,비동기 응답이 늦게 와도 언마운트된 뒤엔 state 를 건드리지 않도록 막는 가드로 씀
  const mountedRef = useRef(true);
  // 마지막 라운드 답변 뒤 결과 화면으로 넘어가는 예약 타이머, 언마운트 시 취소함
  const resultsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      mountedRef.current = false;
      if (resultsTimerRef.current !== null) {
        clearTimeout(resultsTimerRef.current);
        resultsTimerRef.current = null;
      }
    };
  }, []);

  async function handleStart() {
    setStatus("busy");
    try {
      const data = await reviewGuessStart();
      if (!mountedRef.current) return;
      setSession(data);
      setCurrentRoundIndex(0);
      setSelectedGameId(null);
      setAnswer(null);
      setScore(0);
      setPhase("playing");
      setStatus("idle");
    } catch (error) {
      if (!mountedRef.current) return;
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  // 결과 조회, 마지막 라운드 답변 뒤 자동 호출(정상 경로) 뿐 아니라 그게 실패했을 때 재시도 버튼에서도 다시 씀
  async function loadResults(sessionId: string) {
    if (!mountedRef.current) return;
    setStatus("busy");
    try {
      const data = await reviewGuessResult(sessionId);
      if (!mountedRef.current) return;
      setResult(data);
      setPhase("results");
      setStatus("idle");
    } catch (error) {
      if (!mountedRef.current) return;
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  async function handleSelectGame(gameId: number) {
    // selectedGameId 를 클릭 즉시 채워서 잠금으로 씀(중복 제출,다른 카드 클릭 방지), busy 는 아래에서 실제 네트워크 대기 구간에만 켬
    if (!session || answered || busy || selectedGameId !== null) return;
    setSelectedGameId(gameId);
    try {
      // 고른 카드를 잠깐 강조해서 보여주는 연출 지연, 게임성상 의미가 있어 유지함
      // 이 구간엔 busy 를 켜지 않아 전체 화면 오버레이가 선택 강조를 덮지 않게 함
      await new Promise((resolve) => setTimeout(resolve, 300));
      if (!mountedRef.current) return;
      // 실제 fetch 를 시작하는 시점부터만 오버레이를 띄움
      setStatus("busy");
      const res = await reviewGuessAnswer(session.sessionId, currentRoundIndex, gameId);
      if (!mountedRef.current) return;
      setAnswer(res);
      setScore(res.currentScore);
      setStatus("idle");
      if (res.gameCompleted) {
        // 정답 피드백을 확인할 시간을 준 뒤 결과 화면으로 넘어감, 게임 연출상 유지함
        resultsTimerRef.current = setTimeout(() => {
          resultsTimerRef.current = null;
          if (mountedRef.current) {
            void loadResults(session.sessionId);
          }
        }, 2000);
      }
    } catch (error) {
      if (!mountedRef.current) return;
      setSelectedGameId(null);
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  function handleNextRound() {
    setCurrentRoundIndex((idx) => idx + 1);
    setSelectedGameId(null);
    setAnswer(null);
    setScore(0);
  }

  function handlePlayAgain() {
    setSession(null);
    setCurrentRoundIndex(0);
    setSelectedGameId(null);
    setAnswer(null);
    setScore(0);
    setResult(null);
    setStatus("idle");
    setPhase("start");
  }

  const gradeCls = result ? gradeTone(result.grade) : null;

  return (
    <div className={styles.page}>
      <Header />

      {showOverlay ? (
        <div className={styles.overlay}>
          <div className={styles.overlaySpinner} aria-hidden="true" />
          <h3>{phase === "start" ? "Preparing game..." : "Checking your answer..."}</h3>
          <p>Please wait a moment</p>
        </div>
      ) : null}

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>Steam Game Review Guess</h1>
          <p className={styles.subtitle}>Read the reviews and guess which game they belong to!</p>
        </section>

        {phase === "start" ? (
          <GlassPanel as="section" className={styles.card}>
            <h2 className={styles.cardTitle}>Game Rules</h2>
            <p className={styles.description}>
              Test your Steam game knowledge! Read game reviews and try to guess which game they belong to.
            </p>
            <div className={styles.rules}>
              <h4>How to Play:</h4>
              <ul>
                <li>You&apos;ll see positive and negative reviews from a Steam game</li>
                <li>Choose the correct game from 3 options</li>
                <li>Complete 7 rounds to get your final score</li>
                <li>All games have 5000+ reviews for quality content</li>
                <li>Try to get the highest accuracy possible!</li>
              </ul>
            </div>
            <button type="button" className={styles.primaryBtn} onClick={handleStart} disabled={busy}>
              {busy ? <span className={styles.spinner} aria-hidden="true" /> : null}
              Start Game
            </button>
            <Notice status={status} />
          </GlassPanel>
        ) : null}

        {phase === "playing" && currentRound ? (
          <GlassPanel as="section" className={styles.gameCard}>
            <div className={styles.gameLayout}>
              <div className={styles.gameLeft}>
                <div className={styles.progressBar}>
                  <div className={styles.progressFill} style={{ width: `${progress.percent}%` }} />
                </div>
                <div className={styles.roundInfo}>
                  <div className={styles.roundTitle}>Round {currentRoundIndex + 1}</div>
                  <div className={styles.scoreDisplay}>
                    Score: {score} / {totalRounds}
                  </div>
                  <div className={styles.roundSubtitle}>Read the reviews and guess the game!</div>
                </div>

                <div className={styles.reviewSection}>
                  <div className={styles.reviewSectionHeader}>
                    <h3>Game Reviews</h3>
                    <p>These are real Steam user reviews. Can you identify the game?</p>
                  </div>

                  <div className={styles.reviewContent}>
                    <div className={`${styles.reviewCard} ${styles.positive}`}>
                      <h4>Positive Review</h4>
                      <div className={styles.reviewText}>{currentRound.positiveReview}</div>
                    </div>
                    <div className={`${styles.reviewCard} ${styles.negative}`}>
                      <h4>Negative Review</h4>
                      <div className={styles.reviewText}>{currentRound.negativeReview}</div>
                    </div>
                  </div>
                </div>
              </div>

              <div className={styles.gameRight}>
                {answer ? (
                  <div className={`${styles.answerFeedback} ${answer.correct ? styles.feedbackCorrect : styles.feedbackIncorrect}`}>
                    {answer.correct ? <CheckIcon /> : <XIcon />}
                    <span>
                      {answer.correct
                        ? `Correct! The answer was "${answer.correctGameName}"`
                        : `Wrong! The correct answer was "${answer.correctGameName}"`}
                    </span>
                  </div>
                ) : null}

                <Notice
                  status={status}
                  onRetry={
                    // 마지막 라운드 답변 뒤 결과 조회가 실패하면 옵션,Next Round 둘 다 막혀 새로고침 말고는 빠져나갈 수단이 없었음
                    // 그 경우에만 결과 조회를 다시 시도할 수 있게 함
                    session && answer?.gameCompleted ? () => void loadResults(session.sessionId) : undefined
                  }
                />

                <div className={styles.gameOptions}>
                  {currentRound.gameOptions.map((game) => {
                    const tone = optionTone(game, answer, selectedGameId);
                    const isPreAnswerSelected = !answered && selectedGameId === game.appid;
                    const cls = [
                      styles.gameOptionCard,
                      isPreAnswerSelected ? styles.optionSelected : "",
                      tone ? styles[tone] : "",
                    ]
                      .filter(Boolean)
                      .join(" ");

                    return (
                      <button
                        key={game.appid}
                        type="button"
                        className={cls}
                        disabled={answered || selectedGameId !== null}
                        onClick={() => handleSelectGame(game.appid)}
                      >
                        {game.imageUrl ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img src={game.imageUrl} alt={game.name} className={styles.optionImage} />
                        ) : null}
                        <div className={styles.optionInfo}>
                          <div className={styles.optionTitle}>{game.name}</div>
                          <div className={styles.optionDescription}>{game.description || "No description available"}</div>
                        </div>
                      </button>
                    );
                  })}
                </div>

                {answer && !answer.gameCompleted ? (
                  <button type="button" className={styles.nextBtn} onClick={handleNextRound}>
                    Next Round
                  </button>
                ) : null}
              </div>
            </div>
          </GlassPanel>
        ) : null}

        {phase === "results" && result ? (
          <GlassPanel as="section" className={styles.card}>
            <h2 className={styles.cardTitle}>Final Results</h2>
            <div className={styles.resultHeader}>
              <div className={styles.finalScore}>
                {result.correctAnswers}/{result.totalRounds}
              </div>
              <div className={[styles.grade, gradeCls ? styles[gradeCls] : ""].filter(Boolean).join(" ")}>{result.grade}</div>
              <div className={styles.accuracy}>{result.accuracy.toFixed(1)}% Accuracy</div>
            </div>

            <div className={styles.roundResults}>
              {result.roundResults.map((round) => (
                <div
                  key={round.roundIndex}
                  className={[styles.roundResultRow, round.correct ? styles.feedbackCorrect : styles.feedbackIncorrect].join(" ")}
                >
                  <div className={styles.roundNumber}>Round {round.roundIndex + 1}</div>
                  <div className={styles.roundResultDetails}>
                    <div>Your answer: {round.selectedGameName}</div>
                    <div>Correct answer: {round.correctGameName}</div>
                  </div>
                  <div className={styles.resultIcon}>{round.correct ? <CheckIcon /> : <XIcon />}</div>
                </div>
              ))}
            </div>

            <button type="button" className={styles.primaryBtn} onClick={handlePlayAgain}>
              Play Again
            </button>
          </GlassPanel>
        ) : null}
      </main>
    </div>
  );
}
