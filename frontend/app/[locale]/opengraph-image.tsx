import { ImageResponse } from "next/og";

export const alt = "Playalike: Steam game recommendations";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

// 공유 미리보기 이미지, 기본 글꼴이 라틴 문자만 그려서 두 언어 모두 영어 문구로 쓴다
export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
          background: "linear-gradient(135deg, #0b1020 0%, #1b1745 55%, #10294a 100%)",
          color: "#ffffff",
        }}
      >
        <div style={{ fontSize: 96, fontWeight: 700, letterSpacing: 4 }}>PLAYALIKE</div>
        <div style={{ marginTop: 24, fontSize: 40, color: "#b9c3ff" }}>Steam game recommendations</div>
      </div>
    ),
    size,
  );
}
