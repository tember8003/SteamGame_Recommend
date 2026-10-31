"use client";

import { useMemo, useState } from "react";
import Header from "@/components/Header";
import WorkshopIntro from "@/components/WorkshopIntro";
import GlassPanel from "@/components/ui/GlassPanel";
import RecommendResults, { type ReviewDisplayOptions } from "@/components/RecommendResults";
import { useT } from "@/lib/i18n/useT";
import { recommendRandom } from "@/lib/api";
import { mergeShownAppids, toExcludeAppids } from "@/lib/reroll";
import type { RecommendationResult } from "@/lib/types";
import styles from "./page.module.css";

// random.html 의 태그 목록을 그대로 옮김
const TAGS_LIST = [
  "2D", "3D", "RPG", "Action", "Adventure", "Casual", "Indie", "Strategy", "Simulation",
  "Multiplayer", "Singleplayer", "Co-op", "Early Access", "Open World", "Rich Story",
  "Puzzle", "Platformer", "Shooter", "FPS", "Third Person", "VR", "Fantasy", "Sci-fi", "Horror", "Survival",
  "Anime", "Visual Novel", "Pixel Graphics", "Turn-Based", "Card Game", "Sandbox",
  "Building", "Crafting", "Mystery", "Comedy", "Dark", "Gore", "Violent", "Cute",
  "Psychological Horror", "Detective", "Zombie", "Online Co-Op", "Hack and Slash", "Fighting", "Beat 'em up",
  "Bullet Hell", "Side Scroller", "Roguelike", "Roguelite", "Action RPG", "Action-Adventure",
  "MMO", "JRPG", "Dungeon Crawler", "Match 3", "Sports", "Racing", "First-Person", "Third-Person Shooter",
  "Rhythm", "Music", "Management", "Clicker", "Tactical", "Stealth", "Exploration",
];

// random.html 의 리뷰 섹션 구성(제목 없음, 시간 통계 있음, 추천율 막대 없음, 원문 있음)
const REVIEW_OPTIONS: ReviewDisplayOptions = {
  showHeader: false,
  showHours: true,
  showRatingBar: false,
  showReviewText: true,
};

type Status = "idle" | "loading" | "error" | "rateLimited";
type TagTab = "include" | "exclude";

function isRateLimitError(error: unknown): boolean {
  return error instanceof Error && /\b429\b/.test(error.message);
}

function isNoMatchError(error: unknown): boolean {
  return error instanceof Error && /\b404\b/.test(error.message);
}

// 원본 random.html(태그를 고르면 그 태그들로 게임을 추천받는 화면)을 /steam/random 라우트로 옮김
export default function RandomRecommendPage() {
  const t = useT();
  const [activeTab, setActiveTab] = useState<TagTab>("include");
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [excludedTags, setExcludedTags] = useState<string[]>([]);

  const [review, setReview] = useState(1000);
  const [koreanOnly, setKoreanOnly] = useState(false);
  const [freeOnly, setFreeOnly] = useState(false);

  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [shownAppids, setShownAppids] = useState<number[]>([]);
  const [status, setStatus] = useState<Status>("idle");

  const filteredTags = useMemo(() => {
    const term = searchTerm.trim().toLowerCase();
    if (!term) return TAGS_LIST;
    return TAGS_LIST.filter((tag) => tag.toLowerCase().includes(term));
  }, [searchTerm]);

  function toggleTag(list: string[], setList: (v: string[]) => void, tag: string) {
    setList(list.includes(tag) ? list.filter((t) => t !== tag) : [...list, tag]);
  }

  async function runRecommend(isFresh: boolean) {
    setStatus("loading");
    try {
      const data = await recommendRandom(selectedTags, {
        review,
        koreanCheck: koreanOnly,
        freeCheck: freeOnly,
        excludedTag: excludedTags.length > 0 ? excludedTags : null,
        excludeAppids: isFresh ? undefined : toExcludeAppids(shownAppids),
      });
      setResult(data);
      setShownAppids((prev) => mergeShownAppids(isFresh ? [] : prev, data.recommendedGame));
      setStatus("idle");
    } catch (error) {
      // 조건에 맞는 게임이 없다는 404 는 오류가 아니라 빈 결과로 보여준다
      if (isNoMatchError(error)) {
        setResult({ usedTags: selectedTags, recommendedGame: [], review: [] });
        setStatus("idle");
        return;
      }
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

  const loading = status === "loading";
  const games = result?.recommendedGame ?? [];
  const activeList = activeTab === "include" ? selectedTags : excludedTags;
  const setActiveList = activeTab === "include" ? setSelectedTags : setExcludedTags;
  const namePrefix = activeTab === "include" ? "tag" : "excluded";

  return (
    <div className={styles.page}>
      <Header />

      {loading ? (
        <div className={styles.overlay}>
          <div className={styles.overlaySpinner} aria-hidden="true" />
          <h3>{t("random.overlayTitle")}</h3>
          <p>{t("common.pleaseWait")}</p>
        </div>
      ) : null}

      <main className={styles.main}>
        <WorkshopIntro place="explorer" title={t("random.title")} subtitle={t("random.subtitle")} />

        <div className={styles.layout}>
          <div className={styles.leftPanel}>
            <GlassPanel as="div" className={styles.panel}>
              <h2 className={styles.panelTitle}>{t("random.panelTitle")}</h2>

              <div className={styles.tabSwitcher}>
                <button
                  type="button"
                  className={`${styles.tabBtn} ${activeTab === "include" ? styles.tabBtnActive : ""}`}
                  onClick={() => setActiveTab("include")}
                >
                  {t("random.include")}
                </button>
                <button
                  type="button"
                  className={`${styles.tabBtn} ${activeTab === "exclude" ? styles.tabBtnActive : ""}`}
                  onClick={() => setActiveTab("exclude")}
                >
                  {t("random.exclude")}
                </button>
              </div>

              <div className={styles.searchBox}>
                <input
                  type="text"
                  className={styles.searchInput}
                  placeholder={t("random.searchTags")}
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  aria-label={t("random.searchTagsLabel")}
                />
                {searchTerm ? (
                  <button
                    type="button"
                    className={styles.clearSearchBtn}
                    aria-label={t("random.clearSearch")}
                    onClick={() => setSearchTerm("")}
                  >
                    ×
                  </button>
                ) : null}
              </div>

              {filteredTags.length === 0 ? (
                <p className={styles.noTagsFound}>{t("random.noTagsFound")}</p>
              ) : (
                <div className={styles.tagGrid}>
                  {filteredTags.map((tag) => {
                    const checked = activeList.includes(tag);
                    const id = `${namePrefix}-${tag}`;
                    return (
                      <div key={tag} className={styles.tagItem}>
                        <input
                          type="checkbox"
                          id={id}
                          checked={checked}
                          onChange={() => toggleTag(activeList, setActiveList, tag)}
                        />
                        <label htmlFor={id} className={styles.tagLabel}>
                          {tag}
                        </label>
                      </div>
                    );
                  })}
                </div>
              )}
            </GlassPanel>

            <GlassPanel as="div" className={styles.controlsPanel}>
              <div className={styles.controlItem}>
                <label htmlFor="random-review">{t("common.minReviews")}</label>
                <input
                  type="number"
                  id="random-review"
                  min={0}
                  max={100000}
                  value={review}
                  onChange={(e) => setReview(Number(e.target.value))}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="random-korean">{t("common.koreanOnly")}</label>
                <input
                  type="checkbox"
                  id="random-korean"
                  checked={koreanOnly}
                  onChange={(e) => setKoreanOnly(e.target.checked)}
                />
              </div>
              <div className={styles.controlItem}>
                <label htmlFor="random-free">{t("common.freeOnly")}</label>
                <input
                  type="checkbox"
                  id="random-free"
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
            <h2 className={styles.resultTitle}>{t("random.resultTitle")}</h2>

            {status === "error" ? (
              <div className={styles.notice}>
                <h3>{t("common.errorTitle")}</h3>
                <p>{t("common.tryAgainLater")}</p>
              </div>
            ) : status === "rateLimited" ? (
              <div className={styles.notice}>
                <h3>{t("common.rateTitle")}</h3>
                <p>{t("common.rateHint")}</p>
              </div>
            ) : result && games.length === 0 ? (
              <div className={styles.notice}>
                <h3>{t("common.noGames")}</h3>
                <p>{t("random.noGamesHint")}</p>
              </div>
            ) : result && games.length > 0 ? (
              <RecommendResults
                usedTags={result.usedTags}
                games={games}
                reviews={result.review}
                context="RANDOM"
                reviewOptions={REVIEW_OPTIONS}
                onRecommendAgain={handleRecommendAgain}
                recommendAgainDisabled={loading}
              />
            ) : (
              <div className={styles.notice}>
                <h3>{t("random.ready")}</h3>
                <p>{t("random.readyHint")}</p>
              </div>
            )}
          </GlassPanel>
        </div>
      </main>
    </div>
  );
}
