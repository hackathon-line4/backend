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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;
import com.sonulim.domain.location.domain.LastLocationRepository;
import com.sonulim.domain.location.domain.LocationFailureReason;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.location.domain.LocationRequestStatus;
import com.sonulim.domain.settings.domain.LocationSharing;
import com.sonulim.domain.settings.domain.LocationSharingRepository;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FulfillLocationApiIntegrationTest {

	private static final String CITY_HALL = """
			{"latitude":37.566500,"longitude":126.978000,"accuracyM":18,"capturedAt":"2026-11-04T18:00:04"}""";

	@Autowired private MockMvc mockMvc;
	@Autowired private LocationRequestRepository locationRequestRepository;
	@Autowired private LastLocationRepository lastLocationRepository;
	@Autowired private LocationSharingRepository locationSharingRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private JwtTokenProvider jwtTokenProvider;

	private UUID linkId;
	private User guardian;
	private User recipient;
	private String recipientToken;

	@BeforeEach
	void setUp() {
		locationRequestRepository.deleteAll();
		lastLocationRepository.deleteAll();
		linkId = UUID.randomUUID();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
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

	private LocationRequest openRequest(LocalDateTime requestedAt, Duration timeout) {
		return locationRequestRepository.save(LocationRequest.open(linkId, guardian.getId(), recipient.getId(),
				requestedAt, timeout));
	}

	private ResultActions fulfill(UUID id, String token, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/location-requests/{id}/fulfill", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void sendingALocationStoresItAndFinishesTheRequest() throws Exception {
		LocationRequest request = openRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		fulfill(request.getId(), recipientToken, CITY_HALL).andExpect(status().isNoContent());

		assertThat(locationRequestRepository.findById(request.getId()).orElseThrow().getStatus())
				.isEqualTo(LocationRequestStatus.FULFILLED);
		assertThat(lastLocationRepository.findById(recipient.getId())).hasValueSatisfying(last -> {
			assertThat(last.getLatitude()).isEqualByComparingTo("37.566500");
			assertThat(last.getAccuracyM()).isEqualTo(18);
			// 주소는 변환이 정해진 뒤 채운다
			assertThat(last.getAddress()).isNull();
		});
	}

	@Test
	void aSecondLocationOverwritesTheFirstInsteadOfPilingUp() throws Exception {
		fulfill(openRequest(LocalDateTime.now(), Duration.ofSeconds(30)).getId(), recipientToken, CITY_HALL)
				.andExpect(status().isNoContent());

		fulfill(openRequest(LocalDateTime.now(), Duration.ofSeconds(30)).getId(), recipientToken,
				"""
				{"latitude":35.179554,"longitude":129.075641,"accuracyM":40,"capturedAt":"2026-11-04T19:00:00"}""")
				.andExpect(status().isNoContent());

		// 이동 경로를 쌓지 않는다 — 당사자당 한 건만 남는다
		assertThat(lastLocationRepository.count()).isEqualTo(1);
		assertThat(lastLocationRepository.findById(recipient.getId()).orElseThrow().getLongitude())
				.isEqualByComparingTo("129.075641");
	}

	@Test
	void aLocationThatArrivesAfterTheDeadlineIsStillKept() throws Exception {
		// 보호자 화면은 이미 '실패'를 보여 줬지만, 늦게라도 온 위치를 버릴 이유는 없다
		LocationRequest overdue = openRequest(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30));
		overdue.markTimedOut();
		locationRequestRepository.save(overdue);

		fulfill(overdue.getId(), recipientToken, CITY_HALL).andExpect(status().isNoContent());

		assertThat(locationRequestRepository.findById(overdue.getId()).orElseThrow().getStatus())
				.isEqualTo(LocationRequestStatus.TIMED_OUT);
		assertThat(lastLocationRepository.findById(recipient.getId())).isPresent();
	}

	@Test
	void aLocationSentWhileSharingIsOffIsThrownAway() throws Exception {
		shareLocation(false);
		LocationRequest request = openRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		fulfill(request.getId(), recipientToken, CITY_HALL).andExpect(status().isNoContent());

		LocationRequest stored = locationRequestRepository.findById(request.getId()).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(LocationRequestStatus.FAILED);
		assertThat(stored.getFailureReason()).isEqualTo(LocationFailureReason.SHARING_OFF);
		assertThat(lastLocationRepository.count()).isZero();
	}

	@Test
	void reportingAFailureKeepsTheReasonAndLeavesTheOldLocationAlone() throws Exception {
		fulfill(openRequest(LocalDateTime.now(), Duration.ofSeconds(30)).getId(), recipientToken, CITY_HALL)
				.andExpect(status().isNoContent());
		LocationRequest second = openRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		fulfill(second.getId(), recipientToken, "{\"error\":\"PERMISSION_DENIED\"}")
				.andExpect(status().isNoContent());

		assertThat(locationRequestRepository.findById(second.getId()).orElseThrow().getFailureReason())
				.isEqualTo(LocationFailureReason.PERMISSION_DENIED);
		// 예전 위치가 지워질 이유는 없다
		assertThat(lastLocationRepository.findById(recipient.getId())).isPresent();
	}

	@Test
	void aLocationWithoutCoordinatesIsRejected() throws Exception {
		LocationRequest request = openRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		fulfill(request.getId(), recipientToken, "{\"accuracyM\":18}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("latitude"));
	}

	@Test
	void theGuardianCannotSendTheLocation() throws Exception {
		LocationRequest request = openRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		fulfill(request.getId(), jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN"), CITY_HALL)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherRecipientGets404() throws Exception {
		LocationRequest request = openRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		fulfill(request.getId(), jwtTokenProvider.createAccessToken(UUID.randomUUID(), "RECIPIENT"), CITY_HALL)
				.andExpect(status().isNotFound());
	}
}
