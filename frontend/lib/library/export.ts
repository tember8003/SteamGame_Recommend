import type { Locale } from '../i18n/locales';
import type { LibrarySnapshot } from './api';
import { libraryCopy } from './copy';
import { fillLibraryText, hoursText, numberText, percentText } from './presentation';

/** Deliberately accepts only report fields: no profile, identifier, avatar or share URL is drawn. */
export function libraryImageSections(data: LibrarySnapshot, locale: Locale) {
  const c = libraryCopy[locale];
  const h = (value: number) => `${hoursText(value, locale)} ${c.hourUnit}`;
  return [
    { title: c.ledger, subtitle: c.ledgerNames[data.ledger.code] ?? c.unknown, lines: [
      `${c.owned}: ${numberText(data.stats.ownedCount, locale)} ${c.games}`,
      `${c.unplayed}: ${data.stats.zeroPlayCount} ${c.games} (${percentText(data.stats.zeroPlayRatio, locale)})`,
      `${c.hours}: ${h(data.stats.totalHours)}`, `${c.recent}: ${h(data.stats.recentHours)}`,
    ], note: fillLibraryText(data.ledger) },
    { title: c.verdict, subtitle: c.crime, lines: [fillLibraryText(data.verdict.crime), c.sentence, fillLibraryText(data.verdict.sentence)], note: c.verdictNote },
    { title: c.abilities, subtitle: 'PLAYER ATTRIBUTES', lines: data.gamerStats.map(stat => `${stat.label}: ${stat.value === null ? c.insufficient : `${stat.value} / 100`}`), note: c.statsNote },
    { title: c.diagnosis, subtitle: `${c.favorite}: ${data.diagnosis.favoriteTag ?? c.insufficient}`, lines: [
      `${c.hidden}: ${data.diagnosis.hiddenTaste ?? c.insufficient}`,
      ...data.diagnosis.concentratedGames.map(game => `${game.name || `App ${game.appid}`} / ${h(game.hours)} (${percentText(game.ratio, locale)})`),
    ], note: data.limitedData ? c.limited : c.coverage },
  ];
}

export async function saveLibraryImage(data: LibrarySnapshot, locale: Locale, index?: number): Promise<string> {
  await document.fonts.load('700 32px "Village Galmuri"');
  await document.fonts.ready;
  const sections = libraryImageSections(data, locale);
  const combined = index === undefined;
  const canvas = document.createElement('canvas');
  canvas.width = combined ? 1600 : 1080;
  canvas.height = combined ? 2000 : 1350;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas unavailable');
  ctx.fillStyle = '#0d2027'; ctx.fillRect(0, 0, canvas.width, canvas.height);
  const font = '"Village Galmuri", sans-serif';
  ctx.fillStyle = '#e1c183'; ctx.font = `bold 36px ${font}`; ctx.fillText('PLAYALIKE / THE ARCHIVIST', 48, 70);
  const colors = [['#e7d5ad', '#293530'], ['#ead3bc', '#753e37'], ['#19383b', '#eadcba'], ['#182b3b', '#c7dcc3']];
  const draw = (section: typeof sections[number], i: number, x: number, y: number, w: number, h: number) => {
    const [background, ink] = colors[i];
    ctx.fillStyle = '#a28a60'; ctx.fillRect(x, y, w, h);
    ctx.fillStyle = background; ctx.fillRect(x + 8, y + 8, w - 16, h - 16);
    ctx.strokeStyle = ink; ctx.lineWidth = 2; ctx.strokeRect(x + 20, y + 20, w - 40, h - 40);
    ctx.save(); ctx.beginPath(); ctx.rect(x + 30, y + 28, w - 60, h - 56); ctx.clip();
    ctx.fillStyle = ink;
    let lineY = y + 65;
    const write = (text: string, size: number, maxLines: number) => {
      ctx.font = `bold ${size}px ${font}`;
      const lines: string[] = []; let line = '';
      for (const word of text.match(/\S+\s*|\s+/g) ?? []) {
        if (ctx.measureText(word).width <= w - 88) {
          if (line && ctx.measureText(line + word).width > w - 88) { lines.push(line.trimEnd()); line = word; }
          else line += word;
        } else for (const char of Array.from(word)) {
          if (ctx.measureText(line + char).width > w - 88 && line) { lines.push(line.trimEnd()); line = char; } else line += char;
        }
      }
      if (line) lines.push(line);
      lines.slice(0, maxLines).forEach((value, n) => { ctx.fillText(value + (n === maxLines - 1 && lines.length > maxLines ? '…' : ''), x + 44, lineY); lineY += size * 1.6; });
    };
    write(`ARCHIVE / 0${i + 1}`, 22, 1); lineY += 10;
    write(section.title, combined ? 38 : 48, 2); lineY += 20;
    write(section.subtitle, combined ? 27 : 34, 2); lineY += 25;
    section.lines.forEach((line, n) => {
      write(line, combined ? 27 : 34, 2);
      if (i === 2) {
        const value = data.gamerStats[n]?.value;
        ctx.fillStyle = '#0c2529'; ctx.fillRect(x + 44, lineY - 22, w - 88, 16);
        if (value !== null && value !== undefined) {
          ctx.fillStyle = '#e1c183'; ctx.fillRect(x + 44, lineY - 22, (w - 88) * value / 100, 16);
        }
        lineY += 20;
      }
      lineY += 14; ctx.fillStyle = ink;
    });
    if (i === 1) {
      ctx.save(); ctx.translate(x + w - 280, y + h - 340); ctx.rotate(-0.08);
      ctx.strokeStyle = '#9a433b'; ctx.fillStyle = '#9a433b'; ctx.lineWidth = 4;
      ctx.strokeRect(0, 0, 220, 64); ctx.font = `bold 28px ${font}`;
      ctx.fillText(locale === 'ko' ? '판정 완료' : 'FILED', 25, 43); ctx.restore(); ctx.fillStyle = ink;
    }
    lineY = Math.max(lineY + 18, y + h - (combined ? 180 : 230));
    write(section.note, combined ? 25 : 30, 4);
    ctx.restore();
  };
  if (combined) sections.forEach((section, i) => draw(section, i, 40 + (i % 2) * 780, 110 + Math.floor(i / 2) * 920, 740, 880));
  else draw(sections[index], index, 40, 110, 1000, 1160);
  ctx.fillStyle = '#e1c183'; ctx.font = `22px ${font}`; ctx.fillText('playalike.online', 48, canvas.height - 22);
  const url = canvas.toDataURL('image/png');
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = `playalike-library-${combined ? 'four-cards' : index + 1}.png`;
  document.body.appendChild(anchor); anchor.click(); anchor.remove();
  return url;
}
