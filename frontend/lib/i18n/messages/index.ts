import type { Locale } from "../locales";
import { en, type Messages } from "./en";
import { ko } from "./ko";

export function getMessages(locale: Locale): Messages {
  return locale === "ko" ? ko : en;
}
