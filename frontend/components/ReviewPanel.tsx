"use client";

import { useState } from "react";
import { useT } from "@/lib/i18n/useT";
import type { ReviewResponse } from "@/lib/types";
import styles from "./ReviewPanel.module.css";

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
  const t = useT();
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
            {expanded ? t("review.showLess") : t("review.showMore")}
          </button>
        ) : null}
      </div>
    </div>
  );
}

// 게임 한 편의 리뷰 통계,원문을 보여줌, 데이터가 없으면 안내 문구만 남김
export default function ReviewPanel({ review, options }: { review: ReviewResponse | undefined; options: ReviewDisplayOptions }) {
  const t = useT();
  const hasData = !!review && Object.keys(review).length > 0;
  const header = options.showHeader ? (
    <div className={styles.reviewHeader}>
      <h4>{t("review.info")}</h4>
    </div>
  ) : null;

  if (!hasData) {
    return (
      <div className={styles.reviewSection}>
        {header}
        <p className={styles.noReviewData}>{t("review.noData")}</p>
      </div>
    );
  }

  const positiveRating = review?.positiveRating ?? 0;
  const reviewSummary = review?.reviewSummary || t("review.noRating");
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
          <div className={styles.statLabel}>{options.showHours ? t("review.positive") : t("review.positiveRating")}</div>
          {options.showRatingBar ? (
            <div className={styles.ratingBar}>
              <div className={styles.ratingFill} style={{ width: `${positiveRating}%` }} />
            </div>
          ) : null}
        </div>
        <div className={styles.statItem}>
          <span className={styles.statValue}>{reviewSummary}</span>
          <div className={styles.statLabel}>{options.showHours ? t("review.rating") : t("review.overallRating")}</div>
        </div>
        {options.showHours ? (
          <>
            <div className={styles.statItem}>
              <span className={styles.statValue}>{t("review.hours", { h: positiveHours })}</span>
              <div className={styles.statLabel}>{t("review.posHours")}</div>
            </div>
            <div className={styles.statItem}>
              <span className={styles.statValue}>{t("review.hours", { h: negativeHours })}</span>
              <div className={styles.statLabel}>{t("review.negHours")}</div>
            </div>
          </>
        ) : null}
      </div>

      {options.showReviewText && (positiveReview || negativeReview) ? (
        <div className={styles.reviewTexts}>
          {positiveReview ? <ReviewText label={t("review.positiveReview")} tone="positive" text={positiveReview} /> : null}
          {negativeReview ? <ReviewText label={t("review.negativeReview")} tone="negative" text={negativeReview} /> : null}
        </div>
      ) : null}
    </div>
  );
}
