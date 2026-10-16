"use client";

import { useRef, useState } from "react";
import Header from "@/components/Header";
import GlassPanel from "@/components/ui/GlassPanel";
import MatchResults from "@/components/MatchResults";
import SegmentedControl from "@/components/ui/SegmentedControl";
import { type ReviewDisplayOptions } from "@/components/ReviewPanel";
import { useT } from "@/lib/i18n/useT";
import { recommendMatch } from "@/lib/api";
import { mergeShownAppids, toExcludeAppids } from "@/lib/reroll";
import type { MatchResult } from "@/lib/types";
import styles from "./page.module.css";

// input 페이지와 같은 리뷰 섹션 구성(제목 있음, 시간 통계 없음, 추천율 막대 있음, 원문 없음)
const REVIEW_OPTIONS: ReviewDisplayOptions = {
  showHeader: true,
  showHours: false,
  showRatingBar: true,
  showReviewText: false,
};

type Status = "idle" | "loading" | "error" | "rateLimited";
type SessionValue = "" | "SHORT" | "MEDIUM" | "LONG";

// 429 응답은 lib/api.ts 가 `POST ... failed: 429` 형태의 메시지로 던지므로 여기서 상태코드만 골라냄
function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

// /match 엔드포인트를 노출하는 화면, 자연어 서술 + 구조화 제약으로 요청을 만들고 게임마다 사유/주의점을 보여줌
export default function MatchView({ initialQuery }: { initialQuery: string }) {
  const t = useT();
  // 서버가 읽어 준 ?q= 를 서술 박스 초기값으로만 씀, 자동 제출은 하지 않음
  const [rawText, setRawText] = useState(initialQuery);
  const [sessionLength, setSessionLength] = useState<SessionValue>("");
  const [budgetUsd, setBudgetUsd] = useState("");
  const [players, setPlayers] = useState("");
  const [review, setReview] = useState(100);
  const [koreanOnly, setKoreanOnly] = useState(false);
  const [freeOnly, setFreeOnly] = useState(false);

  const [result, setResult] = useState<MatchResult | null>(null);
  const [shownAppids, setShownAppids] = useState<number[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  const [invalid, setInvalid] = useState(false);

  const textRef = useRef<HTMLTextAreaElement>(null);

  // 백엔드 RecommendationCriteria.isEmpty 와 정렬함, 주 제약(서술/세션/예산/인원) 중 하나라도 있으면 유효한 요청임
  const hasPrimary =
    rawText.trim().length > 0 ||
    sessionLength !== "" ||
    budgetUsd.trim() !== "" ||
    players.trim() !== "";

  // isFresh 가 true 면 새 검색(이전에 나온 게임을 제외하지 않음), false 면 다시추천(누적된 appid 를 제외함)
  async function runMatch(isFresh: boolean) {
    if (!hasPrimary) {
      setInvalid(true);
      textRef.current?.focus();
      setTimeout(() => setInvalid(false), 2000);
      return;
    }

    setStatus("loading");
    try {
      const data = await recommendMatch({
        rawText: rawText.trim(),
        sessionLength: sessionLength === "" ? null : sessionLength,
        budgetUsd: budgetUsd.trim() === "" ? null : Number(budgetUsd),
        players: players.trim() === "" ? null : Number(players),
        review,
        korean: koreanOnly,
        free: freeOnly,
        excludeAppids: isFresh ? undefined : toExcludeAppids(shownAppids),
      });
      setResult(data);
      setShownAppids((prev) => mergeShownAppids(isFresh ? [] : prev, data.games));
      setStatus("idle");
    } catch (error) {
      setResult(null);
      setStatus(isRateLimitError(error) ? "rateLimited" : "error");
    }
  }

  function handleSubmit() {
    void runMatch(true);
  }

  function handleRecommendAgain() {
    void runMatch(false);
  }

  const loading = status === "loading";
  const games = result?.games ?? [];

  return (
    <div className={styles.page}>
      <Header />

      <main className={styles.main}>
        <section className={styles.intro}>
          <h1 className={styles.title}>{t("match.title")}</h1>
          <p className={styles.subtitle}>{t("match.subtitle")}</p>
        </section>

        <div className={styles.layout}>
          <GlassPanel as="aside" className={styles.panel}>
            <h2 className={styles.panelTitle}>{t("match.panelTitle")}</h2>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="match-text">
                {t("match.describeLabel")}
              </label>
              <textarea
                id="match-text"
                ref={textRef}
                className={`${styles.textarea} ${invalid ? styles.invalid : ""}`}
                placeholder={t("match.describePlaceholder")}
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
              />
            </div>

            <div className={styles.controls}>
              <h3 className={styles.controlsTitle}>{t("match.constraints")}</h3>
              <div className={styles.controlItem}>
                <label>{t("match.session.label")}</label>
                <SegmentedControl
                  ariaLabel={t("match.session.label")}
                  value={sessionLength}
                  onChange={(v) => setSessionLength(v as SessionValue)}
                  options={[
                    { value: "", label: t("match.session.any") },
                    { value: "SHORT", label: t("match.session.short") },
                    { value: "MEDIUM", label: t("match.session.medium") },
                    { value: "LONG", label: t("match.session.long") },
                  ]}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="match-budget">{t("match.budget")}</label>
                <input
                  type="number"
                  id="match-budget"
                  min={0}
                  placeholder={t("common.any")}
                  value={budgetUsd}
                  onChange={(e) => setBudgetUsd(e.target.value)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="match-players">{t("match.players")}</label>
                <input
                  type="number"
                  id="match-players"
                  min={1}
                  placeholder={t("common.any")}
                  value={players}
                  onChange={(e) => setPlayers(e.target.value)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="match-review">{t("match.minReviews")}</label>
                <input
                  type="number"
                  id="match-review"
                  min={0}
                  max={100000}
                  value={review}
                  onChange={(e) => setReview(Number(e.target.value))}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="match-korean">{t("match.koreanOnly")}</label>
                <input
                  type="checkbox"
                  id="match-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="match-free">{t("match.freeOnly")}</label>
                <input
                  type="checkbox"
                  id="match-free"
                  checked={freeOnly}
                  onChange={(e) => setFreeOnly(e.target.checked)}
                />
              </div>
            </div>

            <button type="button" className={styles.submitBtn} onClick={handleSubmit} disabled={loading}>
              {loading ? <span className={styles.spinner} aria-hidden="true" /> : null}
              {t("match.submit")}
            </button>
          </GlassPanel>

          <GlassPanel as="section" className={styles.resultPanel}>
            <div className={styles.resultHeader}>
              <h2 className={styles.resultTitle}>{t("match.resultTitle")}</h2>
              <p className={styles.resultSubtitle}>{t("match.resultSubtitle")}</p>
            </div>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>{t("match.errorTitle")}</h3>
                <p>{t("match.errorHint")}</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>{t("match.rateTitle")}</h3>
                <p>{t("match.rateHint")}</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>{t("match.noMatch")}</h3>
                <p>{t("match.noMatchHint")}</p>
              </div>
            ) : result && games.length > 0 ? (
              <>
                {result.criteria.nlParseFailed ? (
                  <p className={styles.parseNotice}>
                    {t("match.parseFailed")}
                  </p>
                ) : null}
                <MatchResults
                  games={games}
                  reviews={result.reviews}
                  explanations={result.explanations}
                  shortfall={result.shortfall}
                  reviewOptions={REVIEW_OPTIONS}
                  onRecommendAgain={handleRecommendAgain}
                  recommendAgainDisabled={loading}
                />
              </>
            ) : (
              <div className={styles.notice}>
                <h3>{t("match.ready")}</h3>
                <p>{t("match.readyHint")}</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
