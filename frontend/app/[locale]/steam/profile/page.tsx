"use client";

import { useRef, useState } from "react";
import SteamIdGuide from "@/components/SteamIdGuide";
import { normalizeSteamId } from "@/lib/steamId";
import Header from "@/components/Header";
import WorkshopIntro from "@/components/WorkshopIntro";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { useT } from "@/lib/i18n/useT";
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
    if (status === "loading") return;
    const trimmed = normalizeSteamId(steamId);
    if (!trimmed) {
      setInvalid(true);
      steamIdRef.current?.focus();
      return;
    }

    setInvalid(false);
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
        <WorkshopIntro place="archive" title={t("profile.title")} subtitle={t("profile.subtitle")} />

        <div className={styles.layout}>
          <GlassPanel as="aside" className={styles.panel}>
            <h2 className={styles.panelTitle}>{t("profile.panelTitle")}</h2>

            <div className={styles.warning}>{t("profile.warning")}</div>

            <div className={styles.field}>
              <label className={styles.label} htmlFor="profile-id">
                {t("common.steamId")}
              </label>
              <input
                type="text"
                id="profile-id"
                ref={steamIdRef}
                className={`${styles.textInput} ${invalid ? styles.invalid : ""}`}
                placeholder={t("profile.placeholder")}
                value={steamId}
                onChange={(e) => { setSteamId(e.target.value); setInvalid(false); }}
                autoComplete="off"
                spellCheck={false}
                name="steamId"
                aria-invalid={invalid}
                aria-describedby={invalid ? "steam-id-error" : "steam-id-hint"}
                onKeyDown={handleKeyDown}
              />
            </div>

            <p id="steam-id-hint" className={styles.idHint}>{t("steamGuide.inputHint")}</p>
            {invalid ? <p id="steam-id-error" className={styles.idError} role="alert">{t("steamGuide.invalid")}</p> : null}
            <SteamIdGuide />

            <div className={styles.controls}>
              <h3 className={styles.controlsTitle}>{t("common.options")}</h3>
              <div className={styles.controlItem}>
                <label htmlFor="profile-review">{t("common.reviewCount")}</label>
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
                <label htmlFor="profile-korean">{t("common.koreanSupportOnly")}</label>
                <input
                  type="checkbox"
                  id="profile-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="profile-free">{t("common.freeGamesOnly")}</label>
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
              {t("common.getRecommendations")}
            </button>
          </GlassPanel>

          <GlassPanel as="section" className={styles.resultPanel} aria-busy={loading} aria-live="polite">
            <div className={styles.resultHeader}>
              <h2 className={styles.resultTitle}>{t("profile.resultTitle")}</h2>
              <p className={styles.resultSubtitle}>{t("profile.resultSubtitle")}</p>
            </div>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>{t("common.errorTitle")}</h3>
                <p>{t("profile.errorHint")}</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>{t("common.rateTitle")}</h3>
                <p>{t("common.rateHint")}</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>{t("common.noGames")}</h3>
                <p>{t("profile.noGamesHint")}</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                usedTagsLabel={t("profile.usedTagsLabel")}
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
                <h3>{t("profile.ready")}</h3>
                <p>{t("profile.readyHint")}</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
