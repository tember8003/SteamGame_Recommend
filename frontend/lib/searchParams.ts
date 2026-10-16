// 같은 키가 여러 번 오면 Next 가 배열로 준다, 첫 값만 쓰고 없으면 빈 문자열로 둔다
export function firstQueryValue(value: string | string[] | undefined): string {
  if (Array.isArray(value)) return value[0] ?? "";
  return value ?? "";
}
