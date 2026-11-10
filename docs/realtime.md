# 실시간 전달 (SSE)

앱이 열려 있는 동안 상태 변화를 `GET /api/v1/events`(SSE)로 밀어 준다. 연결 관리와 이벤트 발행 허브는 `global.realtime`에 있고, 각 도메인은 이 허브에 이벤트를 **발행만** 한다([architecture.md](architecture.md#실시간-전달-sse--푸시)). 앱이 닫혀 있을 때의 FCM 푸시는 아직 없다.

기준: Notion API 명세 `GET /api/v1/events` (#39)

## 도메인에서 이벤트 보내기

```java
public interface RealtimeEventPublisher {
	void publish(UUID userId, RealtimeEventType type, Object data);
}
```

- **받는 사람**은 호출하는 쪽이 정한다. 보호자·당사자 둘 다 받는 이벤트는 사람마다 한 번씩 부른다.
- **type**은 `RealtimeEventType` — 명세의 이벤트 명세 표 14개 그대로(`LINK_REQUEST_UPDATED` → `event: link_request.updated`). 새 이벤트는 명세에 먼저 추가한다.
- **data**는 Spring `ObjectMapper`로 JSON이 된다(`LocalDateTime` → `+09:00`). 명세의 `data` 모양(예: `{"linkRequest": {...}}`)대로 넘기고, Entity가 아니라 응답 DTO를 넣는다.
- **트랜잭션 안에서 부르면 커밋된 뒤에 보낸다.** 롤백되면 보내지 않는다. 트랜잭션 밖이면 바로 보낸다.
- 받는 사람이 지금 연결돼 있지 않아도 5분 동안 보관했다가 재연결 때 다시 보낸다.

```java
@Service
public class LinkRequestService {

	private final RealtimeEventPublisher realtimeEvents;

	// ...생성자 주입

	@Transactional
	public LinkRequestDetails approve(AuthenticatedUser user, String requestId) {
		// ... 승인 처리
		realtimeEvents.publish(request.getRecipientId(), RealtimeEventType.LINK_REQUEST_UPDATED,
				Map.of("linkRequest", LinkRequestResponse.from(details)));
		return details;
	}
}
```

단위 테스트에서는 `mock(RealtimeEventPublisher.class)`를 넣고 `verify(realtimeEvents).publish(...)`로 확인한다.

## 연결 동작

| 항목 | 동작 | 근거 |
| --- | --- | --- |
| 인증 | 다른 API와 같은 `Authorization: Bearer`. 없거나 틀리면 401 `UNAUTHORIZED`, 만료면 401 `TOKEN_EXPIRED` | 명세 |
| 사용자별 연결 | 1개. 같은 사용자가 다시 연결하면 이전 연결을 닫는다 | 명세(기기 1대) |
| 이벤트 id | ULID(`ulid-creator`, 같은 밀리초 안에서도 증가) | 명세 |
| ping | 20초마다 `: ping` 주석. 연결 직후에도 한 번 보낸다 | 명세 / 아래 참고 |
| 재연결 | `Last-Event-ID` 뒤의 이벤트를 다시 보낸다. 그 id가 5분보다 오래됐거나 ULID가 아니면 보내지 않는다(앱이 화면 API로 맞춤) | 명세 |
| 토큰 만료 | 연결 timeout을 액세스 토큰 만료 시각까지로 둬서 그때 닫는다. 스트림 중에는 상태 코드를 바꿀 수 없으므로 연결만 닫고, 앱이 다시 연결하면 401 `TOKEN_EXPIRED` | 명세 |

- **연결 직후 ping**: Spring은 SSE 응답 헤더를 첫 쓰기 때 보낸다. 바로 ping을 보내지 않으면 앱은 첫 ping(20초)까지 연결이 열린 것을 모른다. 명세 응답 예시도 `: ping`으로 시작한다.
- **ping 스레드**: `@EnableScheduling` 없이 허브 전용 스레드 1개(`sse-ping`)로 돌린다. 같은 주기에 5분 지난 이벤트를 정리한다.
- **보안 설정**: SSE가 끝날 때의 비동기 디스패치(`DispatcherType.ASYNC`)는 `SecurityConfig`에서 허용한다. 처음 요청에서 이미 인증을 거쳤고, JWT 필터는 이 디스패치에서 다시 돌지 않는다.

## 제약

- 연결과 최근 이벤트는 **서버 메모리**에 둔다. 재시작하면 비고(앱은 연결이 열릴 때마다 화면 API로 맞춘다), 서버를 2대 이상으로 늘리면 Redis pub/sub 등으로 이벤트를 나눠 보내야 한다(명세).
- 같은 사용자에게 여러 스레드가 동시에 발행하면 전송 순서가 id 순서와 다를 수 있다. id는 발행 순서대로 매겨지고 재전송은 id 순서다.

## 후속 작업

- 각 도메인의 실제 발행 연결 — link(`link_request.*`, `link.updated`), messaging, recipient, location, settings. 받는 사람·시점은 명세의 이벤트 명세 표 참고.
- FCM 푸시(앱이 닫혀 있을 때) — 기기 등록(#17) 머지 후.
- 당사자 연결을 `devices.last_seen_at`(마지막 접속 시각)으로 쓰기 — 기기 등록(#17) 머지 후.
- 로그아웃 시 그 사용자의 SSE 연결 닫기 — account 로그아웃에서 부를 메서드를 그때 허브에 추가한다(account 담당자와 함께).
