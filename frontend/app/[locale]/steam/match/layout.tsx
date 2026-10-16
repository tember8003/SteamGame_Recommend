import { metadataFor } from "@/lib/seo/metadata";

// 화면마다 같은 방식으로 메타데이터를 이 레이아웃에서 내보낸다
export const generateMetadata = metadataFor("match");

export default function MatchLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return children;
}
