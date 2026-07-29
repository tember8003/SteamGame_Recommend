"use client";

import { useState } from "react";
import GameCard from "./GameCard";
import type { FeedbackContext, ReviewResponse, SteamApp } from "@/lib/types";
import styles from "./RecommendResults.module.css";

// 원본 3개 페이지의 리뷰 섹션이 서로 달라서(header 유무, 시간 통계 유무, 막대 유무, 원문 유무)
// 페이지가 자신에게 맞는 조합을 넘기도록 옵션으로 뺌
export type ReviewDisplayOptions = {
  showHeader: boolean;
  showHours: boolean;
  showRatingBar: boolean;
  showReviewText: boolean;
};

const REVIEW_TEXT_PREVIEW_LENGTH = 150;

// 긴 리뷰 원문을 미리보기,전체 보기로 토글하는 한 문단, 원본의 150자 자르기 기준을 그대로 씀
function ReviewText({ label, tone, text }: { label: string; tone: "positive" | "negative"; text: string }) {
  const [expanded, setExpanded] = useState(false);
  const isLong = text.length > REVIEW_TEXT_PREVIEW_LENGTH;
  const shown = expanded || !isLong ? text : `${text.slice(0, REVIEW_TEXT_PREVIEW_LENGTH)}...`;

  return (
    <div className={styles.reviewTextSection}>
      <div className={`${styles.reviewTextHeader} ${styles[tone]}`}>{label}</div>
      <div className={styles.reviewTextContent}>
        {shown}
        {isLong ? (
          <button type="button" className={styles.reviewTextToggle} onClick={() => setExpanded((v) => !v)}>
            {expanded ? "Show less" : "Show more"}
          </button>
        ) : null}
      </div>
    </div>
  );
}

// 게임 한 편의 리뷰 통계,원문을 보여줌, 데이터가 없으면 안내 문구만 남김
function ReviewPanel({ review, options }: { review: ReviewResponse | undefined; options: ReviewDisplayOptions }) {
  const hasData = !!review && Object.keys(review).length > 0;
  const header = options.showHeader ? (
    <div className={styles.reviewHeader}>
      <h4>Review Information</h4>
    </div>
  ) : null;

  if (!hasData) {
    return (
      <div className={styles.reviewSection}>
        {header}
        <p className={styles.noReviewData}>No review data available</p>
      </div>
    );
  }

  const positiveRating = review?.positiveRating ?? 0;
  const reviewSummary = review?.reviewSummary || "No rating";
  const positiveHours = review?.positiveHours ?? 0;
  const negativeHours = review?.negativeHours ?? 0;
  const positiveReview = review?.positiveReview ?? "";
  const negativeReview = review?.negativeReview ?? "";

  return (
    <div className={styles.reviewSection}>
      {header}
      <div className={styles.reviewStats}>
        <div className={styles.statItem}>
          <span className={styles.statValue}>{positiveRating}%</span>
          <div className={styles.statLabel}>{options.showHours ? "Positive" : "Positive Rating"}</div>
          {options.showRatingBar ? (
            <div className={styles.ratingBar}>
              <div className={styles.ratingFill} style={{ width: `${positiveRating}%` }} />
            </div>
          ) : null}
        </div>
        <div className={styles.statItem}>
          <span className={styles.statValue}>{reviewSummary}</span>
          <div className={styles.statLabel}>{options.showHours ? "Rating" : "Overall Rating"}</div>
        </div>
        {options.showHours ? (
          <>
            <div className={styles.statItem}>
              <span className={styles.statValue}>{positiveHours}h</span>
              <div className={styles.statLabel}>Pos Hours</div>
            </div>
            <div className={styles.statItem}>
              <span className={styles.statValue}>{negativeHours}h</span>
              <div className={styles.statLabel}>Neg Hours</div>
            </div>
          </>
        ) : null}
      </div>

      {options.showReviewText && (positiveReview || negativeReview) ? (
        <div className={styles.reviewTexts}>
          {positiveReview ? <ReviewText label="Positive Review" tone="positive" text={positiveReview} /> : null}
          {negativeReview ? <ReviewText label="Negative Review" tone="negative" text={negativeReview} /> : null}
        </div>
      ) : null}
    </div>
  );
}

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
