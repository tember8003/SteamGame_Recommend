// 익명 방문자 id 를 localStorage 에 두고 재사용함, 없으면 생성함
// feedback.ts 와 api.ts 가 함께 쓰므로 순환 import 를 피하려고 별도 모듈로 둠

const CLIENT_ID_KEY = 'sgr_client_id'

export function getClientId(): string {
  let id = localStorage.getItem(CLIENT_ID_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(CLIENT_ID_KEY, id)
  }
  return id
}
