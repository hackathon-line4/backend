package com.sonulim.domain.location.api;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRepository;
import com.sonulim.domain.location.domain.LastLocation;
import com.sonulim.domain.location.domain.LastLocationRepository;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationApiIntegrationTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private LocationRequestRepository locationRequestRepository;
	@Autowired private LastLocationRepository lastLocationRepository;
	@Autowired private LocationSharingRepository locationSharingRepository;
	@Autowired private LinkRepository linkRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private LoggingPushSender pushSender;
	@Autowired private JwtTokenProvider jwtTokenProvider;

	private Link link;
	private User guardian;
	private User recipient;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		locationRequestRepository.deleteAll();
		lastLocationRepository.deleteAll();
		linkRepository.deleteAll();
		pushSender.clear();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		link = linkRepository.save(Link.connect(guardian.getId(), recipient.getId(), LocalDateTime.now()));
		guardianToken = jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN");
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

	private void saveLastLocation() {
		lastLocationRepository.saveAndFlush(LastLocation.of(recipient.getId(),
				new BigDecimal("37.566500"), new BigDecimal("126.978000"), 25, LocalDateTime.now()));
	}

	private ResultActions refresh(String token) throws Exception {
		return mockMvc.perform(post("/api/v1/links/{linkId}/location-requests", link.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	private ResultActions view(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/links/{linkId}/location", link.getId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void refreshAsksTheRecipientsPhoneAndWaits() throws Exception {
		refresh(guardianToken)
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.timeoutAt").isNotEmpty());

		assertThat(pushSender.sent()).singleElement().satisfies(push -> {
			assertThat(push.type()).isEqualTo(PushType.LOCATION_REQUEST);
			assertThat(push.recipientUserId()).isEqualTo(recipient.getId());
		});
	}

	@Test
	void pressingRefreshAgainReusesTheRequestInsteadOfWakingThePhoneTwice() throws Exception {
		String first = refresh(guardianToken).andReturn().getResponse().getContentAsString();

		String second = refresh(guardianToken).andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();

		assertThat(second).isEqualTo(first);
		assertThat(locationRequestRepository.count()).isEqualTo(1);
		assertThat(pushSender.sent()).hasSize(1);
	}

	@Test
	void refreshIsRefusedWhileSharingIsOff() throws Exception {
		shareLocation(false);

		refresh(guardianToken)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LOCATION_SHARING_OFF"));

		assertThat(locationRequestRepository.count()).isZero();
		assertThat(pushSender.sent()).isEmpty();
	}

	@Test
	void theViewShowsTheLastLocationAndNoPendingRequest() throws Exception {
		saveLastLocation();

		view(guardianToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.sharingEnabled").value(true))
				.andExpect(jsonPath("$.data.last.latitude").value(37.5665))
				.andExpect(jsonPath("$.data", hasKey("pendingRequest")))
				.andExpect(jsonPath("$.data.pendingRequest").value(nullValue()));
	}

	@Test
	void turningSharingOffHidesALocationThatIsStillStored() throws Exception {
		saveLastLocation();
		shareLocation(false);

		view(guardianToken)
				.andExpect(jsonPath("$.data.sharingEnabled").value(false))
				.andExpect(jsonPath("$.data.last").value(nullValue()));

		// 행은 아직 남아 있지만 보호자에게는 보이지 않는다
		assertThat(lastLocationRepository.findById(recipient.getId())).isPresent();
	}

	@Test
	void theViewShowsARequestThatIsStillWaiting() throws Exception {
		refresh(guardianToken).andExpect(status().isAccepted());

		view(guardianToken)
				.andExpect(jsonPath("$.data.pendingRequest.status").value("PENDING"));
	}

	@Test
	void anOverdueRequestIsNotShownAsStillWaiting() throws Exception {
		locationRequestRepository.save(LocationRequest.open(link.getId(), guardian.getId(), recipient.getId(),
				LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30)));

		// 조회는 읽기만 한다 — 상태를 바꾸지 않고 '기다리는 중'에서만 뺀다
		view(guardianToken).andExpect(jsonPath("$.data.pendingRequest").value(nullValue()));
	}

	@Test
	void refreshingAfterATimeoutStartsANewRequest() throws Exception {
		LocationRequest overdue = locationRequestRepository.save(LocationRequest.open(link.getId(),
				guardian.getId(), recipient.getId(), LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30)));

		refresh(guardianToken).andExpect(status().isAccepted());

		// 기한이 지난 요청은 끝내고 새 요청을 만든다 — 스윕을 기다리지 않는다
		assertThat(locationRequestRepository.findById(overdue.getId()).orElseThrow().isPending()).isFalse();
		assertThat(locationRequestRepository.count()).isEqualTo(2);
		assertThat(pushSender.sent()).hasSize(1);
	}

	@Test
	void theRecipientCannotUseTheGuardianViews() throws Exception {
		String recipientToken = jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT");

		refresh(recipientToken).andExpect(status().isForbidden());
		view(recipientToken).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherGuardianGets404() throws Exception {
		String stranger = jwtTokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN");

		view(stranger).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
