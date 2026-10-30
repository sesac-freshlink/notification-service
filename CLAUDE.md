# notification-service - AI 작업 가이드

> 화면 알림. NOTIFICATION 쪽지를 받아 알림을 저장하고 SSE로 보낸다. 재입고 알림 신청. 담당: 김진아. 포트 8080, 파드 1개.

`README.md`가 이 레포의 진실이다. 쪽지 형식은 .github 레포 `docs/기능이해/화면-알림.md`.

```
⛔ 커밋·push·PR은 사용자가 요청할 때만. main·dev에 직접 push 금지 → 브랜치 + dev 대상 PR
⛔ 비밀값(DB 비밀번호, AWS 키, 토큰 원문) 커밋·출력 금지. 설정은 ${ENV}로
⛔ 알림 종류를 늘리지 않는다 (10/30 확정: EXPIRY_IMMINENT·EXPIRED·RESTOCKED, 10/30 회의로 NEW_ORDER·PICKUP_READY 추가. 이 다섯 가지만)
✅ 인증: gateway가 넘기는 X-User-Id(sub)·X-User-Role·X-Store-Id만 믿는다. 받는 사람은 Viewer.recipients()로 정한다
✅ 쪽지 처리(processed_message)·알림 저장·재입고 신청 삭제는 한 트랜잭션. SSE 전송은 커밋 뒤(@TransactionalEventListener AFTER_COMMIT)
✅ SSE heartbeat는 30초(CloudFront origin 응답 타임아웃)보다 짧게. 파드는 1개 (늘리려면 Redis Pub/Sub 먼저)
✅ 테이블은 Flyway로만. 오류는 BusinessException(NotificationErrorCode.XXX)
✅ 테스트 메서드 이름은 한국어 가능하지만 Gradle 보고서를 위해 LANG=C.UTF-8에서 돌린다
```

검증: `./gradlew build`
