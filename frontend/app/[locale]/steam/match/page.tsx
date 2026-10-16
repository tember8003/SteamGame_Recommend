import { firstQueryValue } from "@/lib/searchParams";
import MatchView from "./MatchView";

// ?q= 를 서버에서 읽어 넘긴다, 화면에서 useSearchParams 를 쓰면 서버 HTML 이 Suspense 대체 화면뿐이 된다
export default async function MatchPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { q } = await searchParams;
  return <MatchView initialQuery={firstQueryValue(q)} />;
}
