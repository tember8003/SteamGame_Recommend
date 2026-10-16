import type { Messages } from "./messages/en";
import { getMessages } from "./messages";
import { makeT, type TFunction } from "./makeT";
import { useLocale } from "./LocaleProvider";

export { makeT, type TFunction };

export function useT(): TFunction {
  return makeT(getMessages(useLocale()));
}

// 현재 로케일 카탈로그를 통째로 준다, 배열,구조화 값처럼 t 로 못 꺼내는 것에 쓴다
export function useMessages(): Messages {
  return getMessages(useLocale());
}
