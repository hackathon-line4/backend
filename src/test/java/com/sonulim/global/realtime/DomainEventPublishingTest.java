package com.sonulim.global.realtime;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.location.application.FulfillLocationService;
import com.sonulim.domain.location.application.LocationRequestTimeoutScheduler;
import com.sonulim.domain.location.application.LocationViewService;
import com.sonulim.domain.location.domain.LastLocationRepository;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.messaging.application.MessageReceiptService;
import com.sonulim.domain.messaging.application.SendMessageService;
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.recipient.application.RecipientReceiptService;
import com.sonulim.domain.recipient.application.RecipientSendService;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.domain.recipient.domain.RecipientSignalRepository;
import com.sonulim.domain.recipient.domain.RecipientSignalType;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.security.AuthenticatedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 내 도메인(messaging·recipient·location)이 SSE 이벤트를 <b>누구에게</b> 보내는지 확인한다.
 * 받는 사람과 data의 키는 API 명세의 이벤트 명세 표 그대로여야 한다 — 틀리면 앱이 아무 반응도 하지 않는다.
 */
@SpringBootTest
class DomainEventPublishingTest {

	@SpyBean private RealtimeEventPublisher realtimeEvents;

	@Autowired private SendMessageService sendMessageService;
	@Autowired private MessageReceiptService messageReceiptService;
	@Autowired private RecipientSendService recipientSendService;
	@Autowired private RecipientReceiptService recipientReceiptService;
	@Autowired private LocationViewService locationViewService;
	@Autowired private FulfillLocationService fulfillLocationService;
	@Autowired private LocationRequestTimeoutScheduler locationRequestTimeoutScheduler;

	@Autowired private MessageRepository messageRepository;
	@Autowired private RecipientSignalRepository recipientSignalRepository;
	@Autowired private HelpRequestRepository helpRequestRepository;
	@Autowired private LocationRequestRepository locationRequestRepository;
	@Autowired private LastLocationRepository lastLocationRepository;
	@Autowired private LocationSharingRepository locationSharingRepository;
	@Autowired private LinkRepository linkRepository;
	@Autowired private SignalRepository signalRepository;
	@Autowired private UserRepository userRepository;

	private Link link;
	private User guardian;
	private User recipient;
	private Signal signal;
	private AuthenticatedUser asGuardian;
	private AuthenticatedUser asRecipient;

	/**
	 * 남겨 두면 다음 테스트가 정리하는 사이에 서버 타이머가 같은 행을 건드려 낙관적 락이 걸린다 — 쓰고 바로 치운다.
	 */
	@AfterEach
	void tearDown() {
		clearRecords();
	}

	@BeforeEach
	void setUp() {
		clearRecords();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		link = linkRepository.save(Link.connect(guardian.getId(), recipient.getId(), LocalDateTime.now()));
		signal = signalRepository.save(Signal.createCustom(link.getId(), "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG)));
		asGuardian = new AuthenticatedUser(guardian.getId(), "GUARDIAN");
		asRecipient = new AuthenticatedUser(recipient.getId(), "RECIPIENT");
		shareLocation(true);
	}

	@Test
	void sendingASignalTellsTheRecipient() {
		sendMessageService.send(asGuardian, link.getId(), signal.getId());

		// 당사자 기기가 바로 울릴 수 있어야 해서 리듬이 담긴 모양으로 간다
		assertThat(dataOf(recipient.getId(), RealtimeEventType.MESSAGE_CREATED)).containsKey("message");
	}

	@Test
	void theDeviceConfirmingTellsTheGuardian() {
		UUID messageId = sendMessageService.send(asGuardian, link.getId(), signal.getId()).message().getId();

		messageReceiptService.ack(asRecipient, messageId, LocalDateTime.now());

		assertThat(dataOf(guardian.getId(), RealtimeEventType.MESSAGE_UPDATED)).containsKey("message");
	}

	@Test
	void callingTheGuardianTellsTheGuardian() {
		recipientSendService.callGuardian(asRecipient, link.getId(), RecipientSignalType.CALL_GUARDIAN);

		assertThat(dataOf(guardian.getId(), RealtimeEventType.RECIPIENT_SIGNAL_CREATED))
				.containsKey("recipientSignal");
	}

	@Test
	void aHelpRequestTellsTheGuardian() {
		recipientSendService.requestHelp(asRecipient, link.getId(), LocationStatus.PENDING, null);

		assertThat(dataOf(guardian.getId(), RealtimeEventType.HELP_REQUEST_CREATED)).containsKey("helpRequest");
	}

	/** 당사자도 받아야 한다 — 보호자가 봤다는 것을 '확인함' 진동으로 알려 주는 유일한 신호다. */
	@Test
	void theGuardianOpeningTellsBothSidesOnce() {
		UUID helpRequestId = recipientSendService
				.requestHelp(asRecipient, link.getId(), LocationStatus.PENDING, null)
				.helpRequest().getId();

		recipientReceiptService.recordHelpRequestOpened(asGuardian, helpRequestId);
		recipientReceiptService.recordHelpRequestOpened(asGuardian, helpRequestId);

		verify(realtimeEvents).publish(eq(guardian.getId()), eq(RealtimeEventType.HELP_REQUEST_UPDATED), any());
		verify(realtimeEvents).publish(eq(recipient.getId()), eq(RealtimeEventType.HELP_REQUEST_UPDATED), any());
	}

	@Test
	void refreshingTheLocationTellsTheRecipientWhichRequestToAnswer() {
		LocationRequest request = locationViewService.refresh(asGuardian, link.getId());

		assertThat(dataOf(recipient.getId(), RealtimeEventType.LOCATION_REQUEST_CREATED))
				.containsEntry("requestId", request.getId());
	}

	@Test
	void theLocationArrivingTellsTheGuardian() {
		LocationRequest request = locationViewService.refresh(asGuardian, link.getId());

		fulfillLocationService.fulfill(asRecipient, request.getId(), new BigDecimal("37.566500"),
				new BigDecimal("126.978000"), 25, LocalDateTime.now());

		assertThat(dataOf(guardian.getId(), RealtimeEventType.LOCATION_UPDATED))
				.containsKeys("location", "requestId");
	}

	@Test
	void aRequestRunningOutOfTimeTellsTheGuardian() {
		// 기한이 이미 지난 요청을 넣어 둔다
		locationRequestRepository.save(LocationRequest.open(link.getId(), guardian.getId(), recipient.getId(),
				LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30)));

		locationRequestTimeoutScheduler.sweep();

		assertThat(dataOf(guardian.getId(), RealtimeEventType.LOCATION_REQUEST_UPDATED))
				.containsKey("locationRequest");
	}

	@Test
	void sharingTurnedOffSendsNoLocationToTheGuardian() {
		LocationRequest request = locationViewService.refresh(asGuardian, link.getId());
		shareLocation(false);

		fulfillLocationService.fulfill(asRecipient, request.getId(), new BigDecimal("37.566500"),
				new BigDecimal("126.978000"), 25, LocalDateTime.now());

		verify(realtimeEvents, never()).publish(any(), eq(RealtimeEventType.LOCATION_UPDATED), any());
		assertThat(dataOf(guardian.getId(), RealtimeEventType.LOCATION_REQUEST_UPDATED))
				.containsKey("locationRequest");
	}

	/** 발행된 data를 꺼낸다 — 받는 사람과 이벤트 종류가 함께 맞아야 통과한다. */
	@SuppressWarnings("unchecked")
	private Map<String, Object> dataOf(UUID userId, RealtimeEventType type) {
		ArgumentCaptor<Object> data = ArgumentCaptor.forClass(Object.class);
		verify(realtimeEvents).publish(eq(userId), eq(type), data.capture());
		return (Map<String, Object>) data.getValue();
	}

	private void clearRecords() {
		messageRepository.deleteAll();
		recipientSignalRepository.deleteAll();
		helpRequestRepository.deleteAll();
		locationRequestRepository.deleteAll();
		lastLocationRepository.deleteAll();
		signalRepository.deleteAll();
		linkRepository.deleteAll();
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, LocalDateTime.now());
		return userRepository.saveAndFlush(user);
	}

	private void shareLocation(boolean enabled) {
		LocationSharing sharing = locationSharingRepository.findById(recipient.getId())
				.orElseGet(() -> LocationSharing.defaults(recipient.getId()));
		sharing.change(enabled, LocalDateTime.now());
		locationSharingRepository.saveAndFlush(sharing);
	}
}
