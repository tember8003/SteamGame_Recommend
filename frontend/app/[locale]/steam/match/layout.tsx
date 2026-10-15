import { metadataFor } from "@/lib/seo/metadata";

// 화면 파일이 클라이언트 컴포넌트라 메타데이터는 이 레이아웃이 내보낸다
export const generateMetadata = metadataFor("match");

export default function MatchLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return children;
}
