import type { GenreProfile } from './api';
import type { Locale } from '../i18n/locales';

const genreLabels: Record<string, [string, string]> = {
  RPG_ADVENTURE: ['RPG·모험', 'RPG & adventure'], STRATEGY_MANAGEMENT: ['전략·경영', 'Strategy & management'],
  ACTION_SHOOTER: ['액션·슈팅', 'Action & shooters'], SURVIVAL_CRAFTING: ['생존·제작', 'Survival & crafting'],
  LIFE_FARMING: ['생활·농장', 'Life & farming'], HORROR_MYSTERY: ['공포·미스터리', 'Horror & mystery'],
  RACING_SPORTS: ['레이싱·스포츠', 'Racing & sports'], PUZZLE: ['퍼즐', 'Puzzle'], VISUAL_NOVEL: ['비주얼 노벨', 'Visual novel'],
};
export const genreName = (code: string, locale: Locale) => genreLabels[code]?.[locale === 'ko' ? 0 : 1] ?? code;

export const baseGenres: Record<string, { id: string; ko: string; en: string; prop: string }> = {
  RPG_ADVENTURE: { id: 'rpg', ko: '달빛 야영지', en: 'Moonlit campsite', prop: 'compass' },
  STRATEGY_MANAGEMENT: { id: 'strategy', ko: '작전 지휘실', en: 'Command outpost', prop: 'chess' },
  ACTION_SHOOTER: { id: 'action', ko: '장비 정비 아지트', en: 'Gear safehouse', prop: 'target' },
  SURVIVAL_CRAFTING: { id: 'survival', ko: '개척 기지', en: 'Frontier camp', prop: 'tools' },
  LIFE_FARMING: { id: 'farming', ko: '아늑한 농가', en: 'Cozy farmhouse', prop: 'plant' },
  HORROR_MYSTERY: { id: 'horror', ko: '달빛 서재', en: 'Midnight study', prop: 'candle' },
  RACING_SPORTS: { id: 'racing', ko: '트로피 차고', en: 'Trophy garage', prop: 'trophy' },
  PUZZLE: { id: 'default', ko: '수수께끼 공방', en: 'Puzzle workshop', prop: 'puzzle' },
  VISUAL_NOVEL: { id: 'default', ko: '이야기 쉼터', en: 'Story retreat', prop: 'books' },
};
const fallback = { id: 'default', ko: '여행자의 거점', en: 'Traveler’s base', prop: null };

export function baseTheme(profile?: GenreProfile | null) {
  const insufficient = profile?.state === 'INSUFFICIENT';
  const primary = !insufficient && profile?.primaryGenre ? baseGenres[profile.primaryGenre] : null;
  const secondary = !insufficient && profile?.secondaryGenre ? baseGenres[profile.secondaryGenre] ?? null : null;
  return {
    ...(primary ?? fallback),
    image: `/images/library/${primary && primary.id !== 'default' ? primary.id : 'rpg'}.webp`,
    secondary, insufficient, mixed: !insufficient && profile?.state === 'MIXED', legacy: !profile,
  };
}

export const baseCopy = {
  ko: {
    eyebrow: 'PLAYER HOME / 나의 플레이 거점', welcome: '당신의 취향이 머무는 곳',
    warehouse: '수집품 창고', gallery: '추억의 전시장', attributes: '캐릭터 상태창', map: '다음 모험 지도',
    stations: '거점의 장소', enter: '살펴보기', guide: '궁금한 장소를 눌러, 내 기록을 둘러보세요.',
    mixed: '여러 세계를 오가는 여행자', secondary: '이런 취향도 함께 머물러요',
    insufficient: '장르를 읽을 기록이 아직 적어, 기본 거점에서 시작해요.',
    coverage: '전체 플레이 시간 중 장르를 분류한 범위', basis: '장르 비중은 분류된 시간 안에서, 게임별 대표 장르를 기준으로 계산해요.',
    archive: '기록관지기', collected: '창고에 모인 게임', sleeping: '아직 떠나지 않은 모험',
    visits: '가장 오래 머문 세 세계', noVisits: '플레이 기록이 쌓이면 이곳에 추억이 전시돼요.',
    genreMap: '내가 걸어온 장르', noGenres: '앞으로의 모험으로 지도를 채워보세요.',
    glimpse: '새롭게 발견한 취향', coopProp: '함께 앉을 동료의 의자', puzzleProp: '퍼즐 도구', booksProp: '이야기책',
    save: '이 장소 이미지 저장', saveAll: '내 거점 이미지 저장', share: '거점 링크 공유',
    recommendation: '다음 모험 고르기', imageTitle: 'MY PLAYER BASE',
    npc: {
      PILE_OF_SHAME: '창고에 아직 시작하지 않은 모험이 기다리고 있군요. 오늘은 하나를 꺼내볼까요?',
      ONE_GAME_DEVOTION: '한 세계에 오래 머문 여행자군요. 그곳에서 쌓은 추억이 전시장의 중심이에요.',
      GENRE_LOYALIST: '마음이 돌아가는 장르가 있군요. 익숙한 즐거움도, 새로운 발견도 챙겨가세요.',
      GENRE_NOMAD: '여러 세계를 두루 여행했군요. 다음 목적지도 한 가지로 정할 필요는 없겠죠.',
      DEFAULT: '지나온 게임들이 이 거점의 풍경이 됐어요. 마음이 가는 곳부터 둘러보세요.',
    } as Record<string, string>,
  },
  en: {
    eyebrow: 'PLAYER HOME / YOUR JOURNEY', welcome: 'A place shaped by your play',
    warehouse: 'Collection storage', gallery: 'Memory exhibition', attributes: 'Character attributes', map: 'Adventure map',
    stations: 'Places in your base', enter: 'Explore', guide: 'Choose a place to explore your game history.',
    mixed: 'A traveler between many worlds', secondary: 'Another taste lives here, too',
    insufficient: 'There is little genre evidence yet. Your journey starts at the default base.',
    coverage: 'Share of total playtime classified by genre', basis: 'Genre shares use classified playtime, assigning each game to one representative genre.',
    archive: 'The archivist', collected: 'Games in storage', sleeping: 'Adventures not yet started',
    visits: 'The three worlds you stayed in longest', noVisits: 'As you play, your memories will appear here.',
    genreMap: 'The genres you have traveled', noGenres: 'Your future adventures will fill this map.',
    glimpse: 'A newly discovered taste', coopProp: 'A chair for a fellow traveler', puzzleProp: 'Puzzle tools', booksProp: 'Storybooks',
    save: 'Save this place', saveAll: 'Save my base image', share: 'Share base link',
    recommendation: 'Choose your next adventure', imageTitle: 'MY PLAYER BASE',
    npc: {
      PILE_OF_SHAME: 'Unstarted adventures are waiting in storage. Shall we take one out today?',
      ONE_GAME_DEVOTION: 'You stayed in one world for a long time. Those memories take pride of place here.',
      GENRE_LOYALIST: 'There is a genre you keep coming back to. Take familiar joys and new discoveries along.',
      GENRE_NOMAD: 'You have traveled across many worlds. Your next destination need not fit one genre.',
      DEFAULT: 'The games you played have shaped this place. Start wherever your curiosity takes you.',
    } as Record<string, string>,
  },
};

// 장르 분위기에 어울리는 동행자를 고른다, 테스트 유형을 계산하지 않는다
export function libraryCompanion(profile?: GenreProfile | null) {
  const characters: Record<string, string> = {
    RPG_ADVENTURE: 'GNHS', STRATEGY_MANAGEMENT: 'GMHS', ACTION_SHOOTER: 'EMHP',
    SURVIVAL_CRAFTING: 'EMCS', LIFE_FARMING: 'GMCP', HORROR_MYSTERY: 'ENHS',
    RACING_SPORTS: 'EMHP', PUZZLE: 'EMCP', VISUAL_NOVEL: 'GNCS',
  };
  const genre = profile?.state === 'READY' ? profile.primaryGenre : null;
  return { code: genre ? characters[genre] ?? 'ENCS' : 'ENCS', genre: genre && characters[genre] ? genre : null };
}
