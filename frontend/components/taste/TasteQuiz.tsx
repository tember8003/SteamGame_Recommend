"use client";
import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import type { Locale } from '@/lib/i18n/locales';
import { recordTasteEvent, tasteErrorText, tasteQuestions, tasteResult, type Answer, type TasteQuestions } from '@/lib/taste/api';
import { useTasteSession } from '@/lib/taste/session';
import styles from './taste.module.css';

export default function TasteQuiz({ locale }: { locale: Locale }) {
  const ko = locale === 'ko';
  const router = useRouter();
  const session = useTasteSession();
  const [questions, setQuestions] = useState<TasteQuestions | null>(null);
  const [answers, setAnswers] = useState<(Answer | undefined)[]>([]);
  const [position, setPosition] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [attempt, setAttempt] = useState(0);
  const started = useRef(false);
  const submitting = useRef(false);
  const needsReload = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  function validate(value: TasteQuestions) {
    if (!value.version || value.questions.length !== 12 || value.questions.some((q, i) => q.id !== i + 1 || q.options.length !== 2 || !q.options.some(o => o.key === 'A') || !q.options.some(o => o.key === 'B'))) throw new Error('Invalid questions');
    return value;
  }
  useEffect(() => {
    const controller = new AbortController();
    tasteQuestions(locale, needsReload.current, controller.signal).then(validate).then(data => { if (!controller.signal.aborted) { needsReload.current = false; setQuestions(data) } })
      .catch(error => { if (!controller.signal.aborted) setError(tasteErrorText(error, locale)) });
    return () => controller.abort();
  }, [locale, attempt]);
  useEffect(() => { heading.current?.focus() }, [position, questions]);
  useEffect(() => {
    if (!cooldown) return;
    const timer = setTimeout(() => setCooldown(value => Math.max(0, value - 1)), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);
  function choose(answer: Answer) {
    if (submitting.current) return;
    if (!started.current) { started.current = true; session.clear(); recordTasteEvent('TEST_START') }
    setAnswers(current => { const next = [...current]; next[position] = answer; return next });
    setError('');
  }
  async function next() {
    if (!questions || !answers[position] || submitting.current || cooldown) return;
    if (position < 11) { setPosition(value => value + 1); return }
    if (answers.length !== 12 || answers.some(value => !value)) return;
    submitting.current = true; setLoading(true); setError('');
    try {
      const result = await tasteResult(questions.version, answers as Answer[], locale);
      session.save(result, locale);
      router.push(`/${locale}/taste/${result.code}`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409 && error.code === 'TASTE_VERSION_MISMATCH') {
        needsReload.current = true;
        setQuestions(null); setAnswers([]); setPosition(0); started.current = false;
        setNotice(ko ? '문항이 업데이트됐어요. 새 문항으로 다시 시작해주세요.' : 'The questions changed. Please start again with the updated version.');
        try { const fresh = validate(await tasteQuestions(locale, true)); needsReload.current = false; setQuestions(fresh) } catch (reloadError) { setError(tasteErrorText(reloadError, locale)) }
      } else {
        setError(tasteErrorText(error, locale));
        if (error instanceof ApiError && error.status === 429) setCooldown(Math.ceil(error.retryAfterSeconds ?? 30));
      }
    } finally { submitting.current = false; setLoading(false) }
  }
  const question = questions?.questions[position];
  return <main id="taste-main" className={styles.main}>
    <nav className={styles.breadcrumb}><Link href={`/${locale}`}>{ko ? '마을로 돌아가기' : 'Back to the village'}</Link><Link href={`/${locale}/taste`}>{ko ? '16가지 유형 도감' : 'All 16 types'}</Link></nav>
    <div className={styles.quiz} aria-busy={loading || (!questions && !error)}>
      {notice ? <p role="status">{notice}</p> : null}
      {question ? <>
        <p className={styles.resultCount}>{ko ? '나의 플레이 취향 찾기' : 'Find your play style'} — {position + 1} / 12</p>
        <progress className={styles.progress} max={12} value={position + 1} aria-label={ko ? '테스트 진행' : 'Test progress'} />
        <h1 ref={heading} tabIndex={-1} className={styles.question}>{position + 1}. {question.text}</h1>
        <div className={styles.answers} role="group" aria-label={ko ? '선택지' : 'Answers'}>
          {question.options.map(option => <button type="button" key={option.key} data-testid={`answer-${option.key}`} aria-pressed={answers[position] === option.key}
            disabled={loading} onClick={() => choose(option.key)}><strong aria-hidden="true">{option.key}</strong>{option.text}</button>)}
        </div>
        <div className={styles.quizActions}>
          <button className={styles.secondaryButton} disabled={position === 0 || loading} onClick={() => { setPosition(value => value - 1); setError('') }}>{ko ? '이전 문항' : 'Previous question'}</button>
          <button className={styles.primaryButton} disabled={!answers[position] || loading || cooldown > 0} onClick={next}>
            {loading ? (ko ? '취향을 읽고 있어요…' : 'Reading your taste…') : cooldown ? `${cooldown}s` : position === 11 ? (ko ? '결과 보기' : 'See my result') : (ko ? '다음 문항' : 'Next question')}
          </button>
        </div>
      </> : !error ? <p role="status">{ko ? '점술가가 질문을 준비하고 있어요…' : 'The fortune teller is preparing your questions…'}</p> : null}
      {error ? <div role="alert" className={styles.apiError}><p>{error}</p>{!questions ? <button className={styles.secondaryButton} onClick={() => { setError(''); setAttempt(n => n + 1) }}>{ko ? '다시 시도' : 'Try again'}</button> : null}</div> : null}
    </div>
  </main>;
}
