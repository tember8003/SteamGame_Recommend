"use client";

import { useEffect, useRef, useState } from "react";
import cytoscape from "cytoscape";
import GlassPanel from "@/components/ui/GlassPanel";
import { graphNeighbors, graphSearch } from "@/lib/api";
import type { GraphNode } from "@/lib/types";
import { edgeId, hasEdge, seedPosition } from "./seedPosition";
import styles from "./SimilarityGraph.module.css";

const SEARCH_DEBOUNCE_MS = 300;
const SEARCH_MIN_LENGTH = 2;
const NEIGHBOR_COUNT = 8;
const SEED_SPREAD = 300;
const FIT_PADDING = 60;

// cytoscape 스타일, 원본 graph.html 의 노드,엣지 스타일을 그대로 옮김
// node[image] 셀렉터로만 배경 이미지를 건다, 빈 문자열 image 는 데이터에 아예 넣지 않으므로
// (addNodeData 참고) 이 셀렉터가 빈 문자열을 만날 일이 없어 스타일 파서가 죽지 않는다
const CY_STYLE: cytoscape.StylesheetJson = [
  {
    selector: "node",
    style: {
      label: "data(name)",
      color: "#fff",
      "font-size": "10px",
      "text-valign": "bottom",
      "text-margin-y": 5,
      "text-wrap": "ellipsis",
      "text-max-width": "90px",
      width: 42,
      height: 42,
      "background-color": "#1e1e2e",
      "background-fit": "cover",
      "border-width": 1,
      "border-color": "#4a4a6a",
    },
  },
  { selector: "node[image]", style: { "background-image": "data(image)" } },
  {
    selector: "node.center",
    style: { "border-color": "#00d4ff", "border-width": 3, width: 60, height: 60 },
  },
  {
    selector: "edge",
    style: {
      width: "mapData(similarity, 0, 1, 1, 7)",
      "line-color": "#3a3a55",
      opacity: 0.7,
      "curve-style": "straight",
    },
  },
];

// image 가 있을 때만 데이터에 넣는다, 빈 문자열을 넣으면 node[image] 셀렉터가 걸려
// background-image 를 빈 문자열로 풀려다 cytoscape 스타일 파서가 죽는다
function addNodeData(data: cytoscape.NodeDataDefinition, image: string | undefined) {
  if (image) {
    data.image = image;
  }
}

// 원본 static/graph.html(임베딩 유사도 그래프 탐색)을 옮긴 컴포넌트, cytoscape 본체를 여기서 다룸
// 페이지 쪽(app/steam/graph/page.tsx)이 ssr:false 로 이 컴포넌트만 클라이언트 전용으로 불러온다
export default function SimilarityGraph() {
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<GraphNode[]>([]);
  const [hintVisible, setHintVisible] = useState(true);

  const containerRef = useRef<HTMLDivElement>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  // cytoscape 인스턴스, 리렌더에 날아가지 않도록 ref 로 보관함
  const cyRef = useRef<cytoscape.Core | null>(null);
  // 이웃까지 펼쳐 넣은(확장 완료한) 노드 id 집합, 같은 노드를 두 번 펼치지 않으려고 씀
  const loadedRef = useRef<Set<string>>(new Set());
  // 자동완성 목록에서 항목을 고른 직후엔 같은 입력값으로 검색을 다시 태우지 않도록 막는 표시임
  const skipNextSearchRef = useRef(false);
  // 매 조회마다 증가하는 요청 번호, 응답이 왔을 때 이 번호가 최신이 아니면 결과를 버림
  const searchRequestIdRef = useRef(0);
  // 디바운스 중인(아직 발화 전) setTimeout id, query 가 안 바뀌는 close 경로에서도 취소하려고 별도로 들고 있음
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // 지금까지 나간 요청을 전부 낡은 것으로 만듦, 늦게 도착하는 응답이 있어도 setSuggestions 를 못 하게 막고
  // 아직 발화 전인 디바운스 타이머도 같이 취소함
  function invalidatePendingSearch() {
    searchRequestIdRef.current += 1;
    if (debounceTimerRef.current !== null) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
  }

  // 노드 id 로 이웃을 조회해 그래프에 덧붙인다, 이미 펼친 노드면 아무 일도 하지 않는다
  async function expand(id: string) {
    const cy = cyRef.current;
    if (!cy || loadedRef.current.has(id)) return;
    try {
      const data = await graphNeighbors(Number(id), NEIGHBOR_COUNT);
      const src = cy.getElementById(id).position();
      const existingEdgeIds = new Set(cy.edges().map((edge) => edge.id()));
      const add: cytoscape.ElementDefinition[] = [];

      (data.neighbors ?? []).forEach((nb) => {
        const nid = String(nb.appid);
        if (cy.getElementById(nid).empty()) {
          const nodeData: cytoscape.NodeDataDefinition = { id: nid, name: nb.name };
          addNodeData(nodeData, nb.image);
          // 새 노드가 전부 같은 좌표면 cose 레이아웃이 서로 못 밀어내므로 소스 주변에 흩어 배치함
          add.push({ group: "nodes", data: nodeData, position: seedPosition(src, SEED_SPREAD) });
        }
        if (!hasEdge(existingEdgeIds, id, nid)) {
          add.push({
            group: "edges",
            data: { id: edgeId(id, nid), source: id, target: nid, similarity: nb.similarity },
          });
        }
      });

      cy.add(add);

      // fit 없으면 새로 들어온 노드가 화면 밖에 그려지므로 layoutstop 이후 뷰포트를 그래프 전체에 맞춘다
      const layout = cy.layout({ name: "cose", animate: false, randomize: false, padding: 60 });
      layout.one("layoutstop", () => cy.fit(cy.elements(), FIT_PADDING));
      layout.run();

      loadedRef.current.add(id);
    } catch {
      // 이웃 조회 실패는 조용히 무시한다, 그래프는 이전 상태를 유지한다
    }
  }

  // 검색 결과에서 고른 게임을 중심 노드로 그래프를 새로 시작한다
  async function startGraph(appid: number, name: string, image: string) {
    const cy = cyRef.current;
    if (!cy) return;
    cy.elements().remove();
    loadedRef.current.clear();
    setHintVisible(false);
    const data: cytoscape.NodeDataDefinition = { id: String(appid), name };
    addNodeData(data, image);
    cy.add({ group: "nodes", data, classes: "center", position: { x: 0, y: 0 } });
    await expand(String(appid));
  }

  // cytoscape 인스턴스는 마운트 시 한 번만 만들고 언마운트 때 destroy 함
  useEffect(() => {
    if (!containerRef.current) return;

    const cy = cytoscape({
      container: containerRef.current,
      style: CY_STYLE,
      layout: { name: "cose", animate: false },
      wheelSensitivity: 0.2,
    });
    cy.on("tap", "node", (evt: cytoscape.EventObjectNode) => {
      void expand(evt.target.id());
    });
    cyRef.current = cy;

    return () => {
      cy.destroy();
      cyRef.current = null;
    };
  }, []);

  // query 가 바뀔 때마다 디바운스 뒤 graphSearch 로 자동완성 후보를 가져옴
  // 목록을 비우는 동기 처리는 이벤트 핸들러(handleQueryChange, selectSuggestion)가 맡고
  // 이 effect 는 비동기 조회 예약,취소만 다룸
  useEffect(() => {
    if (skipNextSearchRef.current) {
      skipNextSearchRef.current = false;
      return;
    }

    const trimmed = query.trim();
    if (trimmed.length < SEARCH_MIN_LENGTH) {
      return;
    }

    const timer = setTimeout(() => {
      debounceTimerRef.current = null;
      // 이 조회 하나만의 번호를 찍어두고, 응답이 왔을 때 더 최신 요청,선택,닫기가 없었는지 확인함
      const requestId = (searchRequestIdRef.current += 1);
      graphSearch(trimmed)
        .then((items) => {
          if (searchRequestIdRef.current === requestId) {
            setSuggestions(items);
          }
        })
        .catch(() => {
          if (searchRequestIdRef.current === requestId) {
            setSuggestions([]);
          }
        });
    }, SEARCH_DEBOUNCE_MS);
    debounceTimerRef.current = timer;

    return () => {
      clearTimeout(timer);
      if (debounceTimerRef.current === timer) {
        debounceTimerRef.current = null;
      }
    };
  }, [query]);

  // 자동완성 바깥을 클릭하면 목록을 닫음, 닫은 뒤엔 대기 중이던 타이머,늦게 오는 응답 모두 무시함
  useEffect(() => {
    function handleOutsideClick(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        invalidatePendingSearch();
        setSuggestions([]);
      }
    }
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, []);

  // 글자 수가 기준(2자) 밑으로 내려가면 자동완성 목록을 바로 비움, 기준 이상이면 effect 가 디바운스 조회를 맡음
  function handleQueryChange(value: string) {
    setQuery(value);
    if (value.trim().length < SEARCH_MIN_LENGTH) {
      invalidatePendingSearch();
      setSuggestions([]);
    }
  }

  function selectSuggestion(item: GraphNode) {
    skipNextSearchRef.current = true;
    invalidatePendingSearch();
    setQuery(item.name);
    setSuggestions([]);
    void startGraph(item.appid, item.name, item.image);
  }

  return (
    <div className={styles.body}>
      <GlassPanel as="div" className={styles.toolbar}>
        <h1 className={styles.title}>Similarity Graph</h1>
        <div ref={wrapperRef} className={styles.searchWrapper}>
          <input
            type="text"
            className={styles.searchInput}
            placeholder="게임 검색 후 선택"
            autoComplete="off"
            value={query}
            onChange={(e) => handleQueryChange(e.target.value)}
          />
          {suggestions.length > 0 ? (
            <div className={styles.autocompleteList}>
              {suggestions.map((item) => (
                <button
                  type="button"
                  key={item.appid}
                  className={styles.autocompleteItem}
                  onClick={() => selectSuggestion(item)}
                >
                  {item.image ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={item.image} alt="" className={styles.autocompleteThumb} />
                  ) : null}
                  <span>{item.name}</span>
                </button>
              ))}
            </div>
          ) : null}
        </div>
      </GlassPanel>

      <div className={styles.graphArea}>
        <div ref={containerRef} className={styles.cyContainer} />
        {hintVisible ? (
          <div className={styles.hint}>게임을 검색해 시작하세요. 노드를 클릭하면 이웃이 펼쳐집니다.</div>
        ) : null}
      </div>
    </div>
  );
}
