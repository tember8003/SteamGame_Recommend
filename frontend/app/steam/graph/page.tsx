"use client";

import dynamic from "next/dynamic";
import Header from "@/components/Header";

// cytoscape 는 window,DOM 에 의존해 서버 렌더링에서 못 돌므로 클라이언트 전용으로만 불러온다
const SimilarityGraph = dynamic(() => import("@/components/graph/SimilarityGraph"), {
  ssr: false,
  loading: () => <p>Loading graph...</p>,
});

// 원본 static/graph.html(임베딩 유사도 그래프 탐색)을 이 라우트로 옮김
// Header 는 서버 렌더링 가능해 여기서 바로 그리고, cytoscape 를 다루는 본체만 동적 로드함
export default function GraphPage() {
  return (
    <>
      <Header />
      <SimilarityGraph />
    </>
  );
}
