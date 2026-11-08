import BarList from './BarList';
import { pct, STEP_LABEL } from './format';
import type { Step } from './types';

// 첫 단계(방문) 대비 비율을 함께 보여 준다
export default function Funnel({ title, steps }: { title: string; steps: Step[] }) {
  const first = steps[0]?.visitors ?? 0;
  return (
    <section aria-label={title}>
      <h2>{title}</h2>
      <BarList ariaLabel={`${title} 단계`} items={steps.map((s, i) => ({
        label: STEP_LABEL[s.step] ?? s.step, value: s.visitors, note: i === 0 ? undefined : pct(s.visitors, first),
      }))} />
    </section>
  );
}
