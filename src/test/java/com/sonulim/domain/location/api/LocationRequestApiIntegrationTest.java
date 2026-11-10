package com.sonulim.domain.location.api;

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

import com.sonulim.domain.location.application.LocationRequestTimeoutScheduler;
import com.sonulim.domain.location.domain.LocationFailureReason;
import com.sonulim.domain.location.domain.LocationRequest;
import com.sonulim.domain.location.domain.LocationRequestRepository;
import com.sonulim.domain.location.domain.LocationRequestStatus;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LocationRequestApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private LocationRequestRepository locationRequestRepository;

	@Autowired
	private LocationRequestTimeoutScheduler scheduler;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private UUID guardianId;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		locationRequestRepository.deleteAll();
		guardianId = UUID.randomUUID();
		guardianToken = jwtTokenProvider.createAccessToken(guardianId, "GUARDIAN");
	}

	private LocationRequest givenRequest(LocalDateTime requestedAt, Duration timeout) {
		return locationRequestRepository.save(LocationRequest.open(UUID.randomUUID(), guardianId,
				UUID.randomUUID(), requestedAt, timeout));
	}

	private ResultActions read(UUID id, String token) throws Exception {
		return mockMvc.perform(get("/api/v1/location-requests/{id}", id)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
	}

	@Test
	void aWaitingRequestReportsItsDeadline() throws Exception {
		LocationRequest request = givenRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		read(request.getId(), guardianToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.timeoutAt").isNotEmpty())
				// 공통 규칙: 값이 없는 필드는 빼지 않고 null로 둔다
				.andExpect(jsonPath("$.data", hasKey("failureReason")))
				.andExpect(jsonPath("$.data.failureReason").value(nullValue()));
	}

	@Test
	void readingAnOverdueRequestReportsItAsTimedOutRightAway() throws Exception {
		// 스윕이 아직 돌지 않았어도 보호자 화면이 '요청 중'에 머물면 안 된다
		LocationRequest request = givenRequest(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30));

		read(request.getId(), guardianToken)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("TIMED_OUT"));

		assertThat(locationRequestRepository.findById(request.getId()).orElseThrow().getStatus())
				.isEqualTo(LocationRequestStatus.TIMED_OUT);
	}

	@Test
	void aFailedRequestKeepsTellingWhy() throws Exception {
		LocationRequest request = givenRequest(LocalDateTime.now(), Duration.ofSeconds(30));
		request.markFailed(LocationFailureReason.PERMISSION_DENIED);
		locationRequestRepository.save(request);

		read(request.getId(), guardianToken)
				.andExpect(jsonPath("$.data.status").value("FAILED"))
				.andExpect(jsonPath("$.data.failureReason").value("PERMISSION_DENIED"));
	}

	@Test
	void theSweepTimesOutRequestsNobodyIsPolling() throws Exception {
		LocationRequest overdue = givenRequest(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30));
		LocationRequest fresh = givenRequest(LocalDateTime.now(), Duration.ofMinutes(10));

		scheduler.sweep();

		assertThat(locationRequestRepository.findById(overdue.getId()).orElseThrow().getStatus())
				.isEqualTo(LocationRequestStatus.TIMED_OUT);
		assertThat(locationRequestRepository.findById(fresh.getId()).orElseThrow().getStatus())
				.isEqualTo(LocationRequestStatus.PENDING);
	}

	@Test
	void aLocationThatArrivedJustBeforeTheSweepIsNotTimedOut() throws Exception {
		LocationRequest overdue = givenRequest(LocalDateTime.now().minusMinutes(5), Duration.ofSeconds(30));
		overdue.markFulfilled();
		locationRequestRepository.save(overdue);

		scheduler.sweep();

		assertThat(locationRequestRepository.findById(overdue.getId()).orElseThrow().getStatus())
				.isEqualTo(LocationRequestStatus.FULFILLED);
	}

	@Test
	void theRecipientCannotPollTheRequest() throws Exception {
		LocationRequest request = givenRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		read(request.getId(), jwtTokenProvider.createAccessToken(UUID.randomUUID(), "RECIPIENT"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void anotherGuardianGets404() throws Exception {
		LocationRequest request = givenRequest(LocalDateTime.now(), Duration.ofSeconds(30));

		read(request.getId(), jwtTokenProvider.createAccessToken(UUID.randomUUID(), "GUARDIAN"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
	}
}
