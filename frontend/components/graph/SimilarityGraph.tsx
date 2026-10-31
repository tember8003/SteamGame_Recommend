"use client";

import { useEffect, useRef, useState } from "react";
import cytoscape from "cytoscape";
import GlassPanel from "@/components/ui/GlassPanel";
import { useT } from "@/lib/i18n/useT";
import { graphNeighbors, graphSearch } from "@/lib/api";
import type { GraphNeighbor, GraphNode } from "@/lib/types";
import {
  angleBetween,
  edgeId,
  fanPositions,
  hasEdge,
  pickChildren,
  ringPositions,
  type Point,
} from "./graphLayout";
import styles from "./SimilarityGraph.module.css";

const SEARCH_DEBOUNCE_MS = 300;
const SEARCH_MIN_LENGTH = 2;
// 첫 겹은 중심을 둘러싼 원에 8개, 그 바깥 가지는 노드마다 3개씩 붙인다
const RING_COUNT = 8;
const BRANCH_COUNT = 3;
// 가지는 이미 화면에 있는 게임을 건너뛰고 3개를 채워야 해서 API 상한(15)까지 받아온다
// 비슷한 게임이 몰린 쪽은 가까운 이웃이 이미 화면에 있어 8개만 받으면 3개를 못 채운다
const BRANCH_FETCH = 15;
const RING_RADIUS = 190;
const BRANCH_LENGTH = 150;
// 가지 하나에서 이웃한 자식끼리 벌리는 각도(라디안), 너무 넓으면 옆 가지와 겹친다
const BRANCH_STEP = 0.42;
const GROW_MS = 550;
// 첫 겹이 거의 다 퍼진 뒤에 바깥 가지가 이어서 퍼지게 늦춘다
const BRANCH_DELAY_MS = 350;
const FIT_PADDING = 40;
const FIT_MS = 400;

// cytoscape 스타일, 바깥 겹으로 갈수록 노드는 작게, 선은 얇고 흐리게 그린다
// node[image] 셀렉터로만 배경 이미지를 건다, 빈 문자열 image 는 데이터에 아예 넣지 않으므로
// (addNodeData 참고) 이 셀렉터가 빈 문자열을 만날 일이 없어 스타일 파서가 죽지 않는다
const CY_STYLE: cytoscape.StylesheetJson = [
  {
    selector: "node",
    style: {
      label: "data(name)",
      color: "#f2e7cb",
      "font-family": "Village Galmuri, sans-serif",
      "font-size": "12px",
      shape: "rectangle",
      "text-valign": "bottom",
      "text-margin-y": 5,
      "text-wrap": "ellipsis",
      "text-max-width": "90px",
      width: 42,
      height: 42,
      "background-color": "#21383b",
      "background-fit": "cover",
      "border-width": 3,
      "border-color": "#a28a60",
    },
  },
  { selector: "node[image]", style: { "background-image": "data(image)" } },
  {
    selector: "node.center",
    style: { "border-color": "#e1c183", "border-width": 4, width: 60, height: 60 },
  },
  {
    selector: "node.outer",
    style: { width: 32, height: 32, "font-size": "12px", "text-max-width": "80px", color: "#d0c9b7" },
  },
  {
    selector: "edge",
    style: { width: 2, "line-color": "#a6c6b1", opacity: 0.55, "curve-style": "straight" },
  },
  // Gemini 유사도는 대부분 0.6 위에 몰려 있어 그 구간을 굵기 차이로 쓴다, 범위 밖 값은 양 끝으로 붙는다
  { selector: "edge.ring", style: { width: "mapData(similarity, 0.6, 1, 1, 2.2)", opacity: 0.7 } },
  // 가지끼리 같은 게임을 공유할 때 잇는 선, 구조를 가리지 않게 가장 흐리게 그린다
  { selector: "edge.cross", style: { width: 0.6, opacity: 0.2, "line-style": "dashed" } },
  // 마우스를 올린 노드는 잘린 이름 전체를 어두운 상자에 담아 보여 주고, 다른 노드와 선 위에 그린다
  {
    selector: "node.hovered",
    style: {
      "text-max-width": "400px",
      "font-size": "11px",
      color: "#f2e7cb",
      "text-background-color": "#102329",
      "text-background-opacity": 0.85,
      "text-background-padding": "4px",
      "text-background-shape": "rectangle",
      "border-color": "#e1c183",
      "z-index": 999,
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

// 한 번 펼칠 때 새로 만든 노드 id 와, 이미 있던 노드와 새로 이은 흐린 선 id
interface Grown {
  nodes: string[];
  crossEdges: string[];
}

function prefersReducedMotion(): boolean {
  return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
}

// 원본 static/graph.html(임베딩 유사도 그래프 탐색)을 옮긴 컴포넌트, cytoscape 본체를 여기서 다룸
// 페이지 쪽(app/steam/graph/page.tsx)이 ssr:false 로 이 컴포넌트만 클라이언트 전용으로 불러온다
export default function SimilarityGraph() {
  const t = useT();
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState<GraphNode[]>([]);
  const [hintVisible, setHintVisible] = useState(true);

  const containerRef = useRef<HTMLDivElement>(null);
  const wrapperRef = useRef<HTMLDivElement>(null);
  // cytoscape 인스턴스, 리렌더에 날아가지 않도록 ref 로 보관함
  const cyRef = useRef<cytoscape.Core | null>(null);
  // 가지까지 펼친 노드 id 집합, 같은 노드를 두 번 펼치지 않으려고 씀
  const loadedRef = useRef<Set<string>>(new Set());
  // 노드마다 애니메이션이 끝났을 때의 자리, 움직이는 도중에도 가지 방향을 이 값으로 정한다
  const homeRef = useRef<Map<string, Point>>(new Map());
  // 노드마다 자기를 펼친 부모 id, 가지를 부모 반대쪽(바깥)으로 뻗을 때 쓴다
  const fromRef = useRef<Map<string, string>>(new Map());
  // 그래프를 새로 시작할 때마다 올리는 번호, 늦게 온 이웃 응답이 새 그래프에 섞이지 않게 막는다
  const generationRef = useRef(0);
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

  // 새 노드를 부모 자리에 만든 뒤 목표 자리로 퍼져 나가게 움직인다, 움직임을 줄이는 설정이면 바로 놓는다
  // 기다리는 동안 부모 자리에 겹쳐 보이지 않게 노드와 선을 투명하게 두었다가 움직이면서 드러낸다
  // 투명도는 잠시 덮어쓴 값이라 끝나면 지워서 스타일시트의 투명도로 돌아가게 한다
  function grow(cy: cytoscape.Core, ids: string[], delay: number, crossEdgeIds: string[] = []) {
    const reduced = prefersReducedMotion();
    if (!reduced) {
      // 이미 있는 노드끼리 새로 이은 선도 가지와 같이 드러나야 먼저 튀어나와 보이지 않는다
      crossEdgeIds.forEach((edgeIdValue) => {
        const edge = cy.getElementById(edgeIdValue);
        if (edge.empty()) return;
        const target = Number(edge.style("opacity"));
        edge.style("opacity", 0);
        edge.delay(delay).animate(
          { style: { opacity: target } },
          { duration: GROW_MS, complete: () => edge.removeStyle("opacity") },
        );
      });
    }
    ids.forEach((id) => {
      const home = homeRef.current.get(id);
      const node = cy.getElementById(id);
      if (!home || node.empty()) return;
      if (reduced) {
        node.position(home);
        return;
      }
      const edges = node.connectedEdges();
      // 스타일시트가 정한 투명도까지만 올려야 끝에서 선이 번쩍이지 않는다
      const edgeTargets = edges.map((edge) => Number(edge.style("opacity")));
      node.style("opacity", 0);
      edges.style("opacity", 0);
      // 한 요소에 animate 를 두 번 걸면 차례로 돌기 때문에 자리와 투명도를 한 번에 건다
      node.delay(delay).animate(
        { position: home, style: { opacity: 1 } },
        { duration: GROW_MS, easing: "ease-out-cubic", complete: () => node.removeStyle("opacity") },
      );
      edges.forEach((edge, i) => {
        edge.delay(delay).animate(
          { style: { opacity: edgeTargets[i] } },
          { duration: GROW_MS, complete: () => edge.removeStyle("opacity") },
        );
      });
    });
  }

  // 퍼지는 움직임이 끝난 뒤 화면을 그래프 전체에 맞춘다, 그사이 새 그래프가 시작됐으면 하지 않는다
  function fitAfter(cy: cytoscape.Core, generation: number, wait: number) {
    const reduced = prefersReducedMotion();
    setTimeout(() => {
      if (cyRef.current !== cy || generationRef.current !== generation) return;
      if (reduced) {
        cy.fit(cy.elements(), FIT_PADDING);
      } else {
        cy.animate({ fit: { eles: cy.elements(), padding: FIT_PADDING } }, { duration: FIT_MS });
      }
    }, reduced ? 0 : wait);
  }

  // parentId 에서 뻗는 새 노드와 선을 넣는다, 새 노드는 부모 자리에서 시작한다
  // 이미 화면에 있는 이웃은 노드를 만들지 않고 흐린 선만 잇는다
  function attach(
    cy: cytoscape.Core,
    parentId: string,
    neighbors: GraphNeighbor[],
    picked: { fresh: string[]; linked: string[] },
    homes: Point[],
    edgeClass: string,
  ): Grown {
    const byId = new Map(neighbors.map((nb) => [String(nb.appid), nb]));
    const start = homeRef.current.get(parentId) ?? cy.getElementById(parentId).position();
    const existingEdgeIds = new Set(cy.edges().map((edge) => edge.id()));
    const add: cytoscape.ElementDefinition[] = [];

    picked.fresh.forEach((nid, i) => {
      const nb = byId.get(nid);
      if (!nb) return;
      const data: cytoscape.NodeDataDefinition = { id: nid, name: nb.name };
      addNodeData(data, nb.image);
      homeRef.current.set(nid, homes[i]);
      fromRef.current.set(nid, parentId);
      add.push({ group: "nodes", data, classes: edgeClass === "ring" ? "" : "outer", position: { ...start } });
      add.push({
        group: "edges",
        classes: edgeClass,
        data: { id: edgeId(parentId, nid), source: parentId, target: nid, similarity: nb.similarity },
      });
    });
    const crossEdges: string[] = [];
    picked.linked.forEach((nid) => {
      if (hasEdge(existingEdgeIds, parentId, nid)) return;
      crossEdges.push(edgeId(parentId, nid));
      add.push({
        group: "edges",
        classes: "cross",
        data: { id: edgeId(parentId, nid), source: parentId, target: nid, similarity: byId.get(nid)?.similarity ?? 0 },
      });
    });

    cy.add(add);
    return { nodes: picked.fresh, crossEdges };
  }

  // 노드에서 부모 반대쪽으로 가지 3개를 뻗는다, 화면에 있는 노드 집합은 호출한 쪽과 공유해 순서대로 늘린다
  function branch(cy: cytoscape.Core, parentId: string, neighbors: GraphNeighbor[], present: Set<string>): Grown {
    const ids = neighbors.map((nb) => String(nb.appid));
    const picked = pickChildren(ids, present, parentId, BRANCH_COUNT);
    const home = homeRef.current.get(parentId) ?? { x: 0, y: 0 };
    const fromId = fromRef.current.get(parentId);
    const fromHome = fromId ? homeRef.current.get(fromId) : undefined;
    // 부모가 없는 중심 노드는 위쪽으로 뻗는다
    const angle = fromHome ? angleBetween(fromHome, home) : -Math.PI / 2;
    const homes = fanPositions(home, angle, picked.fresh.length, BRANCH_LENGTH, BRANCH_STEP);
    picked.fresh.forEach((id) => present.add(id));
    return attach(cy, parentId, neighbors, picked, homes, "branch");
  }

  // 누른 노드에서 바깥쪽으로 가지를 한 겹 더 뻗는다, 이미 펼친 노드면 아무 일도 하지 않는다
  async function expand(id: string) {
    const cy = cyRef.current;
    if (!cy || loadedRef.current.has(id)) return;
    // 부모가 없는 노드는 중심뿐이고, 여기 왔다면 첫 겹 조회가 실패한 것이라 두 겹을 처음부터 다시 펼친다
    if (!fromRef.current.has(id)) {
      const node = cy.getElementById(id);
      void startGraph(Number(id), node.data("name"), node.data("image") ?? "");
      return;
    }
    const generation = generationRef.current;
    loadedRef.current.add(id);
    try {
      const data = await graphNeighbors(Number(id), BRANCH_FETCH);
      if (cyRef.current !== cy || generationRef.current !== generation) return;
      const present = new Set(cy.nodes().map((node) => node.id()));
      const grown = branch(cy, id, data.neighbors ?? [], present);
      grow(cy, grown.nodes, 0, grown.crossEdges);
      fitAfter(cy, generation, GROW_MS);
    } catch {
      // 이웃 조회 실패는 조용히 무시한다, 다시 누르면 재시도할 수 있게 펼침 표시를 지운다
      // 그사이 새 그래프가 시작됐으면 그 그래프의 펼침 표시를 건드리지 않는다
      if (generationRef.current !== generation) return;
      loadedRef.current.delete(id);
    }
  }

  // 검색 결과에서 고른 게임을 중심으로 두 겹을 한 번에 펼친다
  // 첫 겹은 중심을 둘러싼 원, 두 번째 겹은 첫 겹 노드마다 바깥쪽 부채꼴로 놓는다
  async function startGraph(appid: number, name: string, image: string) {
    const cy = cyRef.current;
    if (!cy) return;
    const generation = (generationRef.current += 1);
    // 줄 서 있던 화면 맞춤과 노드 움직임까지 지워야 새 그래프가 엉뚱한 배율로 맞춰지지 않는다
    cy.stop(true, false);
    cy.elements().stop(true, false);
    cy.elements().remove();
    loadedRef.current.clear();
    homeRef.current.clear();
    fromRef.current.clear();
    setHintVisible(false);

    const centerId = String(appid);
    const center = { x: 0, y: 0 };
    const data: cytoscape.NodeDataDefinition = { id: centerId, name };
    addNodeData(data, image);
    homeRef.current.set(centerId, center);
    cy.add({ group: "nodes", data, classes: "center", position: center });
    // 노드가 한 점에 모여 있을 때 fit 하면 지나치게 확대되므로, 퍼지는 동안은 배율을 고정하고 중심만 맞춘다
    cy.zoom(1);
    cy.center(cy.getElementById(centerId));
    loadedRef.current.add(centerId);

    // 가지를 아직 못 붙인 노드, 도중에 실패하면 이 노드들의 펼침 표시를 지워 다시 누를 수 있게 한다
    const pending = new Set<string>([centerId]);
    try {
      const first = await graphNeighbors(appid, RING_COUNT);
      if (cyRef.current !== cy || generationRef.current !== generation) return;
      const neighbors = first.neighbors ?? [];
      const present = new Set([centerId]);
      const picked = pickChildren(neighbors.map((nb) => String(nb.appid)), present, centerId, RING_COUNT);
      const ring = attach(cy, centerId, neighbors, picked, ringPositions(center, picked.fresh.length, RING_RADIUS), "ring").nodes;
      pending.delete(centerId);
      ring.forEach((id) => present.add(id));
      grow(cy, ring, 0);
      // 가지 조회를 기다리는 동안 첫 겹 노드를 누르면 같은 노드에 가지가 두 번 붙으므로 미리 펼친 것으로 표시한다
      ring.forEach((id) => {
        loadedRef.current.add(id);
        pending.add(id);
      });

      // 가지는 첫 겹 노드 순서대로 붙여야 같은 게임을 두 가지가 동시에 차지하지 않는다
      // 그래서 조회는 한꺼번에 보내되 결과는 순서대로 처리한다
      const results = await Promise.allSettled(ring.map((id) => graphNeighbors(Number(id), BRANCH_FETCH)));
      if (cyRef.current !== cy || generationRef.current !== generation) return;
      const grownNodes: string[] = [];
      const crossEdges: string[] = [];
      results.forEach((result, i) => {
        if (result.status !== "fulfilled") {
          // 이 노드만 가지를 못 붙였으니 눌러서 다시 시도할 수 있게 펼침 표시를 지운다
          loadedRef.current.delete(ring[i]);
          pending.delete(ring[i]);
          return;
        }
        const grown = branch(cy, ring[i], result.value.neighbors ?? [], present);
        grownNodes.push(...grown.nodes);
        crossEdges.push(...grown.crossEdges);
        pending.delete(ring[i]);
      });
      grow(cy, grownNodes, BRANCH_DELAY_MS, crossEdges);
      fitAfter(cy, generation, BRANCH_DELAY_MS + GROW_MS);
    } catch {
      // 조회 실패는 조용히 무시한다, 가지를 못 붙인 노드는 눌러서 다시 시도할 수 있게 펼침 표시를 지운다
      // 그사이 새 그래프가 시작됐으면 그 그래프의 펼침 표시를 건드리지 않는다
      if (generationRef.current !== generation) return;
      pending.forEach((id) => loadedRef.current.delete(id));
    }
  }

  // 탭 핸들러는 마운트 때 한 번만 걸리므로, 렌더마다 새로 만들어지는 expand 를 ref 로 넘겨 최신 것을 부른다
  const expandRef = useRef(expand);
  useEffect(() => {
    expandRef.current = expand;
  });

  // cytoscape 인스턴스는 마운트 시 한 번만 만들고 언마운트 때 destroy 함
  // 노드 자리는 코드가 직접 정하므로 자동 배치는 쓰지 않는다
  useEffect(() => {
    if (!containerRef.current) return;

    const cy = cytoscape({
      container: containerRef.current,
      style: CY_STYLE,
      layout: { name: "preset" },
      wheelSensitivity: 0.2,
    });
    cy.on("tap", "node", (evt: cytoscape.EventObjectNode) => {
      void expandRef.current(evt.target.id());
    });
    // 마우스를 올리면 잘린 이름 전체를 보이고, 눌러서 펼칠 수 있다는 걸 커서로 알린다
    const container = containerRef.current;
    cy.on("mouseover", "node", (evt: cytoscape.EventObjectNode) => {
      evt.target.addClass("hovered");
      container.style.cursor = "pointer";
    });
    cy.on("mouseout", "node", (evt: cytoscape.EventObjectNode) => {
      evt.target.removeClass("hovered");
      container.style.cursor = "";
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
        <div ref={wrapperRef} className={styles.searchWrapper}>
          <input
            type="text"
            className={styles.searchInput}
            placeholder={t("graph.searchPlaceholder")}
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
          <div className={styles.hint}>{t("graph.hint")}</div>
        ) : null}
      </div>
    </div>
  );
}
