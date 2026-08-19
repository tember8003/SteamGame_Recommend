"use client";

import GameCard from "./GameCard";
import ReviewPanel, { type ReviewDisplayOptions } from "./ReviewPanel";
import { useT } from "@/lib/i18n/useT";
import type { MatchExplanation, ReviewResponse, SteamApp } from "@/lib/types";
import styles from "./MatchResults.module.css";

// /match 결과 영역, usedTags 가 없고 게임마다 설명이 붙는 점이 RecommendResults 와 다름
export default function MatchResults({
  games,
  reviews,
  explanations,
  shortfall,
  reviewOptions,
  onRecommendAgain,
  recommendAgainDisabled,
}: {
  games: SteamApp[];
  reviews: ReviewResponse[];
  explanations: MatchExplanation[];
  shortfall: boolean;
  reviewOptions: ReviewDisplayOptions;
  onRecommendAgain: () => void;
  recommendAgainDisabled?: boolean;
}) {
  const t = useT();
  return (
    <div className={styles.results}>
      {shortfall ? (
        <p className={styles.shortfall}>{t("results.shortfall")}</p>
      ) : null}

      <div className={styles.grid}>
        {games.map((game, index) => (
          <div key={`${game.appid}-${index}`} className={styles.resultItem}>
            <GameCard
              game={game}
              feedback={{ context: "MATCH", rank: index }}
              explanation={explanations[index]}
            />
            <ReviewPanel review={reviews[index]} options={reviewOptions} />
          </div>
        ))}
      </div>

      <div className={styles.recommendAgainRow}>
        <button
          type="button"
          className={styles.recommendAgainBtn}
          onClick={onRecommendAgain}
          disabled={recommendAgainDisabled}
        >
          {t("common.recommendAgain")}
        </button>
      </div>
    </div>
  );
}
