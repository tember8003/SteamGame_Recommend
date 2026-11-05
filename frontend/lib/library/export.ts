import type { Locale } from '../i18n/locales';
import type { LibrarySnapshot } from './api';
import { libraryCopy } from './copy';
import { baseCopy, baseTheme, genreName } from './base';
import { fillLibraryText, hoursText, numberText, percentText } from './presentation';
import { picksCopy, matchedReason, idleReason, pickedGenreName, briefVisits } from './picks';

function imagePicks(data: LibrarySnapshot, locale: Locale) {
  const p = data.picks; const c = picksCopy[locale];
  return [
    p?.playNext.length ? { title: c.playNext, lines: p.playNext.flatMap(game => [game.name || `App ${game.appid}`, matchedReason(game.matchedTags, locale)]) } : null,
    p?.revisit.length ? { title: c.revisit, lines: p.revisit.flatMap(game => [`${game.name || `App ${game.appid}`} / ${hoursText(game.hours, locale)} ${libraryCopy[locale].hourUnit}`, idleReason(game.idleDays, locale)]) } : null,
    null,
    p?.abandonedGenre ? { title: c.abandoned, lines: [
      `${pickedGenreName(p.abandonedGenre.genre, locale)}: ${briefVisits(p.abandonedGenre.gameCount, locale)}`,
      ...(p.abandonedGenre.games.length ? [p.abandonedGenre.games.join(', ')] : []), c.abandonedHint,
    ] } : null,
  ];
}

const extraHeight = (extra: ReturnType<typeof imagePicks>[number]) => extra ? 140 + extra.lines.length * 90 : 0;

export function libraryImageSize(data: LibrarySnapshot, locale: Locale, index?: number) {
  const extras = imagePicks(data, locale).map(extraHeight);
  return index === undefined ? { width: 1600, height: 2200 + Math.max(extras[0], extras[1]) + Math.max(extras[2], extras[3]) }
    : { width: 1080, height: 1400 + extras[index] };
}

/** Only report fields are drawn; identity and share URLs are deliberately excluded. */
export function libraryImageSections(data: LibrarySnapshot, locale: Locale) {
  const c = libraryCopy[locale]; const b = baseCopy[locale];
  const extras = imagePicks(data, locale);
  const h = (value: number) => `${hoursText(value, locale)} ${c.hourUnit}`;
  return [
    { title: b.warehouse, subtitle: c.ledgerNames[data.ledger.code] ?? c.unknown, lines: [
      `${c.owned}: ${numberText(data.stats.ownedCount, locale)} ${c.games}`,
      `${c.unplayed}: ${data.stats.zeroPlayCount} ${c.games} (${percentText(data.stats.zeroPlayRatio, locale)})`,
      `${c.hours}: ${h(data.stats.totalHours)}`, `${c.recent}: ${h(data.stats.recentHours)}`,
    ], note: fillLibraryText(data.ledger) },
    { title: b.gallery, subtitle: b.visits, lines: data.diagnosis.concentratedGames.map(game => `${game.name || `App ${game.appid}`} / ${h(game.hours)} (${percentText(game.ratio, locale)})`), note: data.diagnosis.concentratedGames.length ? '' : b.noVisits },
    { title: b.attributes, subtitle: 'PLAYER STATUS', lines: data.gamerStats.map(stat => `${stat.label}: ${stat.value === null ? c.insufficient : `${stat.value} / 100`}`), note: c.statsNote },
    { title: b.map, subtitle: b.genreMap, lines: [
      ...(data.genreProfile?.distribution.slice(0, 3).map(genre => `${genreName(genre.code, locale)}: ${percentText(genre.ratio, locale)}`) ?? []),
      `${c.hidden}: ${data.diagnosis.hiddenTaste ?? c.insufficient}`,
    ], note: data.genreProfile ? b.basis : b.noGenres },
  ].map((section, index) => ({ ...section, extra: extras[index] }));
}

export async function saveLibraryImage(data: LibrarySnapshot, locale: Locale, index?: number): Promise<string> {
  await document.fonts.load('700 32px "Village Galmuri"'); await document.fonts.ready;
  const sections = libraryImageSections(data, locale); const theme = baseTheme(data.genreProfile); const b = baseCopy[locale];
  const combined = index === undefined;
  const canvas = document.createElement('canvas');
  const size = libraryImageSize(data, locale, index); canvas.width = size.width; canvas.height = size.height;
  const ctx = canvas.getContext('2d'); if (!ctx) throw new Error('Canvas unavailable');
  const w = canvas.width; const sceneH = Math.round(w * 9 / 16);
  ctx.fillStyle = '#10252b'; ctx.fillRect(0, 0, w, canvas.height);
  const background = new Image();
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Background timeout')), 10000);
    background.onload = () => { clearTimeout(timer); resolve(); }; background.onerror = () => { clearTimeout(timer); reject(new Error('Background unavailable')); }; background.src = theme.image;
  });
  ctx.imageSmoothingEnabled = false; ctx.drawImage(background, 0, 0, w, sceneH);
  ctx.fillStyle = '#071c24d9'; ctx.fillRect(28, 28, w - 56, combined ? 176 : 150);
  const font = '"Village Galmuri", sans-serif';
  ctx.fillStyle = '#e1c183'; ctx.font = `bold ${combined ? 24 : 20}px ${font}`; ctx.fillText('PLAYALIKE / PLAYER HOME', 52, 70);
  ctx.fillStyle = '#f1e4c5'; ctx.font = `bold ${combined ? 46 : 36}px ${font}`; ctx.fillText(theme[locale], 52, combined ? 137 : 120);
  if (theme.mixed) { ctx.font = `bold 20px ${font}`; ctx.fillText(b.mixed, 52, combined ? 175 : 160); }
  if (theme.prop === 'books' || theme.prop === 'puzzle') {
    ctx.fillStyle = theme.prop === 'books' ? '#bf896c' : '#a9a1cc';
    for (let n = 0; n < 3; n++) ctx.fillRect(w * .53 + (n % 2) * 10, sceneH * .61 + n * 16, 60, 12);
  }
  const write = (text: string, x: number, y: number, maxWidth: number, size: number, maxLines: number) => {
    ctx.font = `bold ${size}px ${font}`;
    const lines: string[] = []; let line = '';
    for (const word of text.match(/\S+\s*|\s+/g) ?? []) {
      if (ctx.measureText(word).width <= maxWidth) {
        if (line && ctx.measureText(line + word).width > maxWidth) { lines.push(line.trimEnd()); line = word; } else line += word;
      } else for (const char of Array.from(word)) {
        if (line && ctx.measureText(line + char).width > maxWidth) { lines.push(line.trimEnd()); line = char; } else line += char;
      }
    }
    if (line) lines.push(line);
    lines.slice(0, maxLines).forEach((value, n) => ctx.fillText(value + (n === maxLines - 1 && lines.length > maxLines ? '…' : ''), x, y + n * size * 1.55));
    return Math.min(maxLines, lines.length) * size * 1.55;
  };
  const draw = (section: typeof sections[number], i: number, x: number, y: number, width: number, height: number) => {
    const coreHeight = height - extraHeight(section.extra);
    ctx.fillStyle = '#a28a60'; ctx.fillRect(x, y, width, height); ctx.fillStyle = '#10252b'; ctx.fillRect(x + 5, y + 5, width - 10, height - 10);
    ctx.save(); ctx.beginPath(); ctx.rect(x + 24, y + 20, width - 48, height - 40); ctx.clip();
    let lineY = y + 55; ctx.fillStyle = '#e4c68b';
    lineY += write(section.title, x + 28, lineY, width - 56, combined ? 32 : 38, 2) + 10;
    ctx.fillStyle = '#bac4b4'; lineY += write(section.subtitle, x + 28, lineY, width - 56, combined ? 20 : 25, 2) + 18;
    for (let n = 0; n < section.lines.length; n++) {
      ctx.fillStyle = '#eee3c9'; lineY += write(section.lines[n], x + 28, lineY, width - 56, combined ? (i === 2 ? 20 : 24) : 28, 2) + 12;
      if (i === 2) {
        const value = data.gamerStats[n]?.value;
        ctx.fillStyle = '#244246'; ctx.fillRect(x + 28, lineY - 12, width - 56, 10);
        if (value !== null && value !== undefined) { ctx.fillStyle = '#e4c68b'; ctx.fillRect(x + 28, lineY - 12, (width - 56) * value / 100, 10); }
        lineY += 24;
      }
    }
    ctx.fillStyle = '#b9c5b4'; write(section.note, x + 28, Math.max(lineY + 12, y + coreHeight - 74), width - 56, combined ? 17 : 21, 3);
    if (section.extra) {
      ctx.fillStyle = '#a28a60'; ctx.fillRect(x + 28, y + coreHeight + 8, width - 56, 2);
      let extraY = y + coreHeight + 60;
      ctx.fillStyle = '#e4c68b'; extraY += write(section.extra.title, x + 28, extraY, width - 56, combined ? 28 : 32, 2) + 18;
      for (const line of section.extra.lines) {
        ctx.fillStyle = '#eee3c9';
        extraY += write(line, x + 28, extraY, width - 56, combined ? 22 : 25, 2) + 20;
      }
    }
    ctx.restore();
  };
  if (combined) sections.forEach((section, i) => draw(section, i, 40 + (i % 2) * 780, sceneH + 30 + Math.floor(i / 2) * (590 + Math.max(extraHeight(sections[0].extra), extraHeight(sections[1].extra))), 740, 560 + extraHeight(section.extra)));
  else draw(sections[index], index, 30, sceneH + 25, w - 60, canvas.height - sceneH - 95);
  ctx.fillStyle = '#e4c68b'; ctx.font = `22px ${font}`; ctx.fillText('playalike.online', 40, canvas.height - 25);
  const url = canvas.toDataURL('image/png'); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `playalike-base-${combined ? 'overview' : index + 1}.png`;
  document.body.appendChild(anchor); anchor.click(); anchor.remove(); return url;
}
