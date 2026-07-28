import StaticMascot, { type MascotState } from "./StaticMascot";

export type { MascotState };

// 마스코트 추상화, v0 은 정적 렌더러만 둠, v3 에서 Rive 렌더러를 같은 인터페이스로 추가함
export default function Concierge({ state = "idle" }: { state?: MascotState }) {
  return <StaticMascot state={state} />;
}
