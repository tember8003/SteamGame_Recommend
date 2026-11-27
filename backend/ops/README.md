# 운영 상태 디스코드 알림

`status_alert.py`가 `/api/metrics/status`를 읽어 디스코드 웹훅으로 보낸다. 경고 판정은 백엔드(`CollectionHealth`)가 하고, 이 스크립트는 상태 변화만 기억해서 전달한다.

서버가 통째로 죽은 경우는 UptimeRobot이 맡는다. 이 스크립트는 서버 안에서 돌기 때문에 서버와 같이 죽는다.

## 보내는 것

- `check`(10분마다): 바뀐 것이 있을 때만 보낸다
  - `[경고]` 새로 생긴 경고: 작업 실패, 주기 초과, 멈춤 추정, 카탈로그 감소, 리뷰 본문 미저장, 상태 API 호출 실패(두 번 연속), 토큰 거부, 디스크 85% 이상
  - `[해결]` 풀린 경고
  - `[알림]` 배포(버전이 바뀐 재시작), 같은 버전 재시작(메모리 초과 의심), 5xx 증가
- `summary`(하루 한 번): 이상이 없어도 경고 수, 버전, 기동 시각, 작업별 마지막 성공, 24시간 리뷰 저장 수, 디스크를 보낸다

## 서버 설치

배포 워크플로는 이미지만 바꾸고 파일은 옮기지 않으므로 처음 한 번은 직접 복사한다.

```
mkdir -p /opt/steam-recommend/ops
# 저장소의 backend/ops/status_alert.py, alert.env.example 을 위 폴더로 복사
cd /opt/steam-recommend/ops
cp alert.env.example alert.env && chmod 600 alert.env   # DISCORD_WEBHOOK_URL 채우기
python3 status_alert.py summary                          # 디스코드에 요약이 오면 설정 완료
```

`crontab -e`에 추가(서버 시계는 UTC):

```
*/10 * * * * flock -n /tmp/sgr-alert.lock python3 /opt/steam-recommend/ops/status_alert.py check >> /opt/steam-recommend/cron-alert.log 2>&1
0 0 * * * python3 /opt/steam-recommend/ops/status_alert.py summary >> /opt/steam-recommend/cron-alert.log 2>&1
```

요약은 00:00 UTC, 한국 시간 09:00에 온다.

## 동작 메모

- 상태 기억 파일은 `ops/alert-state.json`이다. 지우면 다음 실행이 처음 실행처럼 기준만 다시 잡는다(이미 있는 경고는 알림)
- 디스코드로 보내기에 실패하면 상태를 저장하지 않아 다음 실행에서 다시 보낸다
- 상태 API를 못 부르는 동안에는 직전 백엔드 경고를 그대로 둔다, 못 부른 것을 "해결"로 잘못 알리지 않기 위함
- 테스트: `cd backend/ops && python3 -m unittest test_status_alert`
