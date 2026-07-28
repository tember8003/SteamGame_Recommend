"use client";

import { useState } from "react";
import type { MascotState } from "./StaticMascot";

// 앱 상태를 마스코트 상태로 매핑함, v0 은 최소 구현으로 상태값 + setter 만 제공함
export function useMascotState(initial: MascotState = "idle") {
  return useState<MascotState>(initial);
}
