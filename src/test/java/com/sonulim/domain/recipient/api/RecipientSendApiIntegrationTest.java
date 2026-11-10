package com.sonulim.domain.recipient.api;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.domain.recipient.domain.RecipientSignalRepository;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecipientSendApiIntegrationTest {

	private static final String LOCATION_BODY = """
			{"sentAt":"2026-11-04T17:55:02","locationStatus":"ATTACHED",
			"location":{"latitude":37.566500,"longitude":126.978000,"accuracyM":25,
			"capturedAt":"2026-11-04T17:55:01"}}""";

	@Autowired private MockMvc mockMvc;
	@Autowired private RecipientSignalRepository recipientSignalRepository;
	@Autowired private HelpRequestRepository helpRequestRepository;
	@Autowired private LocationSharingRepository locationSharingRepository;
	@Autowired private LinkRepository linkRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private LoggingPushSender pushSender;
	@Autowired private JwtTokenProvider jwtTokenProvider;

	private Link link;
	private User guardian;
	private User recipient;
	private String recipientToken;

	@BeforeEach
	void setUp() {
		recipientSignalRepository.deleteAll();
		helpRequestRepository.deleteAll();
		linkRepository.deleteAll();
		pushSender.clear();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		link = linkRepository.save(Link.connect(guardian.getId(), recipient.getId(), LocalDateTime.now()));
		recipientToken = jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT");
		shareLocation(true);
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

	private ResultActions call(String token, UUID linkId, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/links/{linkId}/recipient-signals", linkId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"CALL_GUARDIAN\",\"sentAt\":\"2026-11-04T15:20:05\"}"));
	}

	private ResultActions help(String token, String body, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/links/{linkId}/help-requests", link.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void callingTheGuardianRecordsItAndSendsAPush() throws Exception {
		call(recipientToken, link.getId(), UUID.randomUUID().toString())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.type").value("CALL_GUARDIAN"))
				.andExpect(jsonPath("$.data.sender.name").value("김하늘"))
				// 당사자 응답에는 보호자 도착·열람이 들어가지 않는다
				.andExpect(jsonPath("$.data.guardianDeliveredAt").value(nullValue()))
				.andExpect(jsonPath("$.data.guardianOpenedAt").value(nullValue()));

		assertThat(pushSender.sent()).singleElement().satisfies(push -> {
			assertThat(push.type()).isEqualTo(PushType.RECIPIENT_SIGNAL);
			assertThat(push.recipientUserId()).isEqualTo(guardian.getId());
		});
	}

	@Test
	void theGuardianCannotCallThemselves() throws Exception {
		call(jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN"), link.getId(),
				UUID.randomUUID().toString())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherRecipientGets404SoTheLinkStaysHidden() throws Exception {
		call(jwtTokenProvider.createAccessToken(UUID.randomUUID(), "RECIPIENT"), link.getId(),
				UUID.randomUUID().toString())
				.andExpect(status().isNotFound());
	}

	@Test
	void retryingWithTheSameKeyDoesNotCallTwice() throws Exception {
		String key = UUID.randomUUID().toString();
		String first = call(recipientToken, link.getId(), key).andReturn().getResponse().getContentAsString();

		String retried = call(recipientToken, link.getId(), key)
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		assertThat(retried).isEqualTo(first);
		assertThat(recipientSignalRepository.count()).isEqualTo(1);
		assertThat(pushSender.sent()).hasSize(1);
	}

	@Test
	void helpRequestKeepsTheLocationWhenSharingIsOn() throws Exception {
		help(recipientToken, LOCATION_BODY, UUID.randomUUID().toString())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.locationStatus").value("ATTACHED"))
				.andExpect(jsonPath("$.data.location.latitude").value(37.5665));

		assertThat(pushSender.sent()).singleElement()
				.satisfies(push -> assertThat(push.type()).isEqualTo(PushType.HELP_REQUEST));
	}

	@Test
	void aLocationThatArrivesWhileSharingIsOffIsNotStored() throws Exception {
		shareLocation(false);

		help(recipientToken, LOCATION_BODY, UUID.randomUUID().toString())
				.andExpect(status().isCreated())
				// 기기가 좌표를 보냈어도 당사자가 꺼 둔 위치는 기록에 들어가지 않는다
				.andExpect(jsonPath("$.data.locationStatus").value("NOT_SHARED"))
				.andExpect(jsonPath("$.data.location").value(nullValue()));

		assertThat(helpRequestRepository.findAll().get(0).getLocation()).isNull();
	}

	@Test
	void aRequestWithoutALocationYetStaysPending() throws Exception {
		help(recipientToken, "{\"sentAt\":\"2026-11-04T17:55:02\",\"locationStatus\":\"PENDING\"}",
				UUID.randomUUID().toString())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.locationStatus").value("PENDING"))
				.andExpect(jsonPath("$.data.location").value(nullValue()));
	}

	@Test
	void helpRequestWithoutAnIdempotencyKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/links/{linkId}/help-requests", link.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + recipientToken)
						.contentType(MediaType.APPLICATION_JSON).content(LOCATION_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}
}
