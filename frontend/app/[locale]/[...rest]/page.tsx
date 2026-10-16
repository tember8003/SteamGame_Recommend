import { notFound } from "next/navigation";

// [locale] 아래에서 맞는 화면이 없는 주소를 언어에 맞는 404 화면으로 보낸다
// 이 파일이 없으면 [locale]/not-found.tsx 가 아니라 기본 404 가 쓰인다
export default function UnknownPage() {
  notFound();
}
