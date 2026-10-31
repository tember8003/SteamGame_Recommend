"use client";

import dynamic from "next/dynamic";
import Header from "@/components/Header";
import WorkshopIntro from "@/components/WorkshopIntro";
import { useT } from "@/lib/i18n/useT";
import styles from "./page.module.css";

// 동적 로드 중 표시, 훅을 쓰려면 컴포넌트라야 해서 별도로 뺀다
function GraphLoading() {
  const t = useT();
  return <p>{t("graph.loading")}</p>;
}

// cytoscape 는 window,DOM 에 의존해 서버 렌더링에서 못 돌므로 클라이언트 전용으로만 불러온다
const SimilarityGraph = dynamic(() => import("@/components/graph/SimilarityGraph"), {
  ssr: false,
  loading: () => <GraphLoading />,
});

// 원본 static/graph.html(임베딩 유사도 그래프 탐색)을 이 라우트로 옮김
// 제목과 설명은 서버 HTML 에 들어가도록 여기서 그리고, cytoscape 를 다루는 본체만 동적 로드함
export default function GraphPage() {
  const t = useT();
  return (
    <>
      <Header />
      <main className={styles.page}>
        <WorkshopIntro place="explorer" title={t("graph.title")} subtitle={t("graph.subtitle")} />
        <SimilarityGraph />
      </main>
    </>
  );
}
