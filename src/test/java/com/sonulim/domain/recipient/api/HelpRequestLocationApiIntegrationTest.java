package com.sonulim.domain.recipient.api;

import java.math.BigDecimal;
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
import com.sonulim.domain.recipient.domain.CapturedLocation;
import com.sonulim.domain.recipient.domain.HelpRequest;
import com.sonulim.domain.recipient.domain.HelpRequestRepository;
import com.sonulim.domain.recipient.domain.LocationStatus;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HelpRequestLocationApiIntegrationTest {

	private static final String ATTACHED_BODY = """
			{"locationStatus":"ATTACHED","location":{"latitude":37.566500,"longitude":126.978000,
			"accuracyM":40,"capturedAt":"2026-11-04T17:55:09"}}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private HelpRequestRepository helpRequestRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private UUID linkId;
	private User guardian;
	private User recipient;
	private String recipientToken;

	@BeforeEach
	void setUp() {
		helpRequestRepository.deleteAll();
		linkId = UUID.randomUUID();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		recipientToken = jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT");
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, LocalDateTime.now());
		return userRepository.saveAndFlush(user);
	}

	private HelpRequest pendingRequest() {
		return helpRequestRepository.save(HelpRequest.send(linkId, recipient.getId(), guardian.getId(),
				LocalDateTime.now(), LocationStatus.PENDING, null));
	}

	private ResultActions attach(UUID id, String token, String body) throws Exception {
		return mockMvc.perform(put("/api/v1/help-requests/{id}/location", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void aPendingRequestGetsItsLocation() throws Exception {
		HelpRequest request = pendingRequest();

		attach(request.getId(), recipientToken, ATTACHED_BODY)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.locationStatus").value("ATTACHED"))
				.andExpect(jsonPath("$.data.location.accuracyM").value(40))
				.andExpect(jsonPath("$.data.sender.name").value("김하늘"))
				// 주소 변환 전이라 비어 있지만 필드는 남긴다
				.andExpect(jsonPath("$.data.location", hasKey("address")))
				.andExpect(jsonPath("$.data.location.address").value(nullValue()));

		// 지도에 찍히는 값이라 소수점 6자리가 그대로 남아야 한다
		assertThat(helpRequestRepository.findById(request.getId()).orElseThrow().getLocation().getLatitude())
				.isEqualByComparingTo("37.566500");
	}

	@Test
	void givingUpOnTheLocationMarksItUnavailable() throws Exception {
		HelpRequest request = pendingRequest();

		attach(request.getId(), recipientToken, "{\"locationStatus\":\"UNAVAILABLE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.locationStatus").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.data.location").value(nullValue()));
	}

	@Test
	void theLocationCannotBeChangedOnceSet() throws Exception {
		HelpRequest request = helpRequestRepository.save(HelpRequest.send(linkId, recipient.getId(),
				guardian.getId(), LocalDateTime.now(), LocationStatus.ATTACHED,
				new CapturedLocation(new BigDecimal("37.566500"), new BigDecimal("126.978000"), 25,
						LocalDateTime.now())));

		attach(request.getId(), recipientToken, ATTACHED_BODY)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LOCATION_ALREADY_SET"));
	}

	@Test
	void attachedWithoutCoordinatesIsRejected() throws Exception {
		HelpRequest request = pendingRequest();

		attach(request.getId(), recipientToken, "{\"locationStatus\":\"ATTACHED\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.field").value("location"));
	}

	@Test
	void aStatusThatIsNotAttachedOrUnavailableIsRejected() throws Exception {
		HelpRequest request = pendingRequest();

		attach(request.getId(), recipientToken, "{\"locationStatus\":\"NOT_SHARED\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.field").value("locationStatus"));
	}

	@Test
	void theGuardianCannotAttachALocation() throws Exception {
		HelpRequest request = pendingRequest();

		attach(request.getId(), jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN"), ATTACHED_BODY)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherRecipientGets404() throws Exception {
		HelpRequest request = pendingRequest();

		attach(request.getId(), jwtTokenProvider.createAccessToken(UUID.randomUUID(), "RECIPIENT"), ATTACHED_BODY)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
