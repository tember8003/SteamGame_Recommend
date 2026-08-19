// choice, review-guess 미니게임이 공통으로 쓰는 라운드 진행 표시 계산만 순수 함수로 모음
// 두 게임 다 라운드 인덱스(0부터 시작),전체 라운드 수로 진행바 퍼센트와 안내 문구를 만듦

export interface RoundProgress {
  percent: number
  roundNumber: number
  totalRounds: number
}

// totalRounds 가 0 이하면(세션이 아직 없거나 빈 응답) 퍼센트는 0, 라운드 번호만 유효함
// 문구는 언어별로 달라 이 함수는 숫자만 내고 라벨 조립은 화면이 맡는다
export function roundProgress(roundIndex: number, totalRounds: number): RoundProgress {
  if (totalRounds <= 0) {
    return { percent: 0, roundNumber: roundIndex + 1, totalRounds: 0 }
  }
  const percent = ((roundIndex + 1) / totalRounds) * 100
  return { percent, roundNumber: roundIndex + 1, totalRounds }
}
