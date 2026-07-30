// choice, review-guess 미니게임이 공통으로 쓰는 라운드 진행 표시 계산만 순수 함수로 모음
// 두 게임 다 라운드 인덱스(0부터 시작),전체 라운드 수로 진행바 퍼센트와 안내 문구를 만듦

export interface RoundProgress {
  percent: number
  label: string
}

// totalRounds 가 0 이하면(세션이 아직 없거나 빈 응답) 퍼센트는 0, 라벨은 라운드 번호만 남김
export function roundProgress(roundIndex: number, totalRounds: number): RoundProgress {
  if (totalRounds <= 0) {
    return { percent: 0, label: `Round ${roundIndex + 1}` }
  }
  const percent = ((roundIndex + 1) / totalRounds) * 100
  return { percent, label: `Round ${roundIndex + 1} of ${totalRounds}` }
}
