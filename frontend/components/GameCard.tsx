"use client";

import { useState } from "react";
import GlassPanel from "./ui/GlassPanel";
import type { FeedbackContext, MatchExplanation, SteamApp } from "@/lib/types";
import { sgrFeedback } from "@/lib/feedback";
import { describeReason, describeCaveat } from "@/lib/explanations";
import { useT } from "@/lib/i18n/useT";
import { siSteam } from "simple-icons";
import styles from "./GameCard.module.css";

// feedback prop 의 형태, 어느 추천 화면(context)에서 몇 번째(rank)로 보였는지 담음
export type GameCardFeedback = {
  context: FeedbackContext;
  rank: number;
  steamId?: string | null;
};

// 게임 한 편을 보여주는 기본 카드, 이름, 설명은 항상 텍스트 렌더만 써서 XSS 를 막음
// feedback prop 을 주면 좋아요,싫어요 버튼과 스토어 링크 클릭 훅이 추가로 붙음(뒤호환, 없으면 기존과 동일)
// explanation prop 을 주면 사유 칩과 주의점 문구가 붙음(뒤호환, 없으면 기존과 동일)
export default function GameCard({
  game,
  className,
  feedback,
  explanation,
}: {
  game: SteamApp;
  className?: string;
  feedback?: GameCardFeedback;
  explanation?: MatchExplanation;
}) {
  const cls = [styles.card, className].filter(Boolean).join(" ");

  const t = useT();

  // 코드를 문구로 옮기고 미지 코드(null)는 걸러냄, 문구는 항상 텍스트 렌더만 씀
  const reasonTexts = (explanation?.reasons ?? [])
    .map((item) => describeReason(item, t))
    .filter((v): v is string => v !== null);
  const caveatTexts = (explanation?.caveats ?? [])
    .map((item) => describeCaveat(item, t))
    .filter((v): v is string => v !== null);
  const hasExplanation = reasonTexts.length > 0 || caveatTexts.length > 0;

  // 응답을 기다리지 않고 눌린 버튼을 바로 표시하기 위한 로컬 상태, 서버 반영 여부와 무관하게 클릭 즉시 갱신함
  const [reaction, setReaction] = useState<"UP" | "DOWN" | null>(null);

  // 좋아요,싫어요 클릭 시 신호를 보냄, feedback 이 없으면 아예 버튼을 렌더하지 않으니 호출도 없음
  // sgrFeedback 은 fire-and-forget 이라 응답을 기다리지 않고, 눌린 버튼 표시는 클릭 즉시 로컬 state 로 갱신함
  function sendReaction(signal: "UP" | "DOWN") {
    if (!feedback) return;
    setReaction(signal);
    sgrFeedback(game.appid, signal, feedback.context, feedback.rank, feedback.steamId ?? undefined);
  }

  // 스토어 링크 클릭 시 약한 양성 신호(CLICK)를 보냄, 이동 자체는 막지 않음
  function sendClick() {
    if (!feedback) return;
    sgrFeedback(game.appid, "CLICK", feedback.context, feedback.rank, feedback.steamId ?? undefined);
  }

  return (
    <GlassPanel as="article" className={cls}>
      <div className={styles.media}>
        {game.headerImage ? (
          <img
            src={game.headerImage}
            alt={game.name}
            className={styles.image}
            loading="lazy"
            onError={(e) => {
              // 이미지 로드 실패 시 깨진 아이콘 대신 배경 그라데이션만 남김
              e.currentTarget.style.display = "none";
            }}
          />
        ) : null}
      </div>

      <div className={styles.body}>
        <h3 className={styles.title}>{game.name}</h3>

        {game.shortDescription ? (
          <p className={styles.desc}>{game.shortDescription}</p>
        ) : null}

        {hasExplanation ? (
          <div className={styles.explanation}>
            {reasonTexts.length > 0 ? (
              <ul className={styles.reasonChips}>
                {reasonTexts.map((text, i) => (
                  <li key={i} className={styles.reasonChip}>
                    {text}
                  </li>
                ))}
              </ul>
            ) : null}
            {caveatTexts.length > 0 ? (
              <ul className={styles.caveats}>
                {caveatTexts.map((text, i) => (
                  <li key={i} className={styles.caveat}>
                    {text}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}

        {feedback ? (
          <div className={styles.feedbackRow}>
            <button
              type="button"
              className={[
                styles.feedbackBtn,
                reaction === "UP" ? styles.feedbackBtnActive : "",
                reaction === "DOWN" ? styles.feedbackBtnDimmed : "",
              ]
                .filter(Boolean)
                .join(" ")}
              aria-label={t("gameCard.like")}
              aria-pressed={reaction === "UP"}
              onClick={() => sendReaction("UP")}
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3H14Z" />
                <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
              </svg>
            </button>
            <button
              type="button"
              className={[
                styles.feedbackBtn,
                reaction === "DOWN" ? styles.feedbackBtnActive : "",
                reaction === "UP" ? styles.feedbackBtnDimmed : "",
              ]
                .filter(Boolean)
                .join(" ")}
              aria-label={t("gameCard.dislike")}
              aria-pressed={reaction === "DOWN"}
              onClick={() => sendReaction("DOWN")}
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3H10Z" />
                <path d="M17 2h3a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-3" />
              </svg>
            </button>
          </div>
        ) : null}

        {game.steamStore ? (
          <a
            href={game.steamStore}
            target="_blank"
            rel="noopener noreferrer"
            className={styles.storeLink}
            onClick={sendClick}
          >
            <svg
              className={styles.storeIcon}
              viewBox="0 0 24 24"
              fill="currentColor"
              aria-hidden="true"
            >
              <path d={siSteam.path} />
            </svg>
            {t("gameCard.viewOnSteam")}
          </a>
        ) : null}
      </div>
    </GlassPanel>
  );
}
