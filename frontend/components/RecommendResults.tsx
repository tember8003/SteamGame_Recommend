"use client";

import GameCard from "./GameCard";
import ReviewPanel, { type ReviewDisplayOptions } from "./ReviewPanel";
import type { FeedbackContext, ReviewResponse, SteamApp } from "@/lib/types";
import styles from "./RecommendResults.module.css";

// ReviewDisplayOptions 는 ReviewPanel 로 옮겼지만, 기존 페이지들이 이 모듈에서 import 하므로 재export 함
export type { ReviewDisplayOptions } from "./ReviewPanel";

// 세 추천 페이지(input, random, similar)가 공통으로 쓰는 결과 영역
// usedTags 칩, GameCard + 리뷰 패널 목록, 다시추천 버튼을 한 번에 그림
export default function RecommendResults({
  usedTags,
  usedTagsLabel = "Used Tags",
  games,
  reviews,
  context,
  steamId,
  reviewOptions,
  onRecommendAgain,
  recommendAgainDisabled,
  recommendAgainLabel = "Recommend Again",
}: {
  usedTags: string[];
  usedTagsLabel?: string;
  games: SteamApp[];
  reviews: ReviewResponse[];
  context: FeedbackContext;
  steamId?: string | null;
  reviewOptions: ReviewDisplayOptions;
  onRecommendAgain: () => void;
  recommendAgainDisabled?: boolean;
  recommendAgainLabel?: string;
}) {
  return (
    <div className={styles.results}>
      {usedTags.length > 0 ? (
        <div className={styles.usedTags}>
          <h4>{usedTagsLabel}</h4>
          <div className={styles.tagList}>
            {usedTags.map((tag) => (
              <span key={tag} className={styles.tag}>
                {tag}
              </span>
            ))}
          </div>
        </div>
      ) : null}

      <div className={styles.grid}>
        {games.map((game, index) => (
          <div key={`${game.appid}-${index}`} className={styles.resultItem}>
            <GameCard game={game} feedback={{ context, rank: index, steamId }} />
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
          {recommendAgainLabel}
        </button>
      </div>
    </div>
  );
}
