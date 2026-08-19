import { en, type Messages } from "./messages/en";
import { ko } from "./messages/ko";
import { useLocale } from "./LocaleProvider";

type Params = Record<string, string | number>;
export type TFunction = (key: string, params?: Params) => string;

// 점경로로 중첩 객체를 걸어 리프 문자열을 찾는다, 문자열이 아니면 undefined
function lookup(source: unknown, key: string): string | undefined {
  let cur: unknown = source;
  for (const part of key.split(".")) {
    if (typeof cur !== "object" || cur === null) return undefined;
    cur = (cur as Record<string, unknown>)[part];
  }
  return typeof cur === "string" ? cur : undefined;
}

// {name} 자리를 params 값으로 바꾼다, 값이 없는 이름은 그대로 둔다
function fill(template: string, params?: Params): string {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (whole, name) => {
    const v = params[name];
    return v == null ? whole : String(v);
  });
}

// 카탈로그 하나에 묶인 t 를 만든다, 조회 실패는 en 폴백 뒤 키 반환한다
export function makeT(messages: Messages): TFunction {
  return (key, params) => {
    const hit = lookup(messages, key) ?? lookup(en, key);
    if (hit == null) {
      if (process.env.NODE_ENV !== "production") {
        console.warn(`[i18n] missing key: ${key}`);
      }
      return key;
    }
    return fill(hit, params);
  };
}

export function useT(): TFunction {
  const { locale } = useLocale();
  return makeT(locale === "ko" ? ko : en);
}

// 현재 로케일 카탈로그를 통째로 준다, 배열,구조화 값처럼 t 로 못 꺼내는 것에 쓴다
export function useMessages(): Messages {
  const { locale } = useLocale();
  return locale === "ko" ? ko : en;
}
