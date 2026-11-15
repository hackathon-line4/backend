package com.sonulim.domain.signal.api;

import java.time.LocalDateTime;
import java.util.List;
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
import com.sonulim.domain.messaging.domain.MessageRepository;
import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.push.LoggingPushSender;
import com.sonulim.global.push.PushType;
import com.sonulim.global.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SignalPracticeApiIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MessageRepository messageRepository;

	@Autowired
	private LinkRepository linkRepository;

	@Autowired
	private SignalRepository signalRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoggingPushSender pushSender;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Link link;
	private User guardian;
	private User recipient;
	private Signal signal;
	private String guardianToken;

	@BeforeEach
	void setUp() {
		messageRepository.deleteAll();
		signalRepository.deleteAll();
		linkRepository.deleteAll();
		pushSender.clear();
		guardian = saveUser("g-" + UUID.randomUUID() + "@example.com", "이영희", Role.GUARDIAN);
		recipient = saveUser("r-" + UUID.randomUUID() + "@example.com", "김하늘", Role.RECIPIENT);
		link = linkRepository.save(Link.connect(guardian.getId(), recipient.getId(), LocalDateTime.now()));
		signal = signalRepository.save(Signal.createCustom(link.getId(), "약 먹을 시간", "저녁 약을 먹자는 뜻",
				SignalCategory.DAILY, "pill", List.of(RhythmBeat.SHORT, RhythmBeat.SHORT, RhythmBeat.LONG)));
		guardianToken = jwtTokenProvider.createAccessToken(guardian.getId(), "GUARDIAN");
	}

	private User saveUser(String email, String name, Role role) {
		User user = User.register(email, "hash", name);
		user.selectRole(role, LocalDateTime.now());
		return userRepository.saveAndFlush(user);
	}

	private ResultActions practice(String token, UUID linkId, UUID signalId, String key) throws Exception {
		return mockMvc.perform(post("/api/v1/links/{linkId}/signals/{signalId}/practice", linkId, signalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header("Idempotency-Key", key));
	}

	@Test
	void sendingPracticeCreatesAPracticeMessageAndPushesIt() throws Exception {
		practice(guardianToken, link.getId(), signal.getId(), UUID.randomUUID().toString())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.kind").value("PRACTICE"))
				.andExpect(jsonPath("$.data.response.status").isEmpty())
				.andExpect(jsonPath("$.data.delivery.status").value("REQUESTED"));

		assertThat(pushSender.sent()).singleElement()
				.satisfies(push -> assertThat(push.type()).isEqualTo(PushType.PRACTICE));
	}

	@Test
	void practicingWithoutAnIdempotencyKeyIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/links/{linkId}/signals/{signalId}/practice", link.getId(), signal.getId())
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + guardianToken))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
	}

	@Test
	void retryingPracticeWithTheSameKeyDoesNotBuzzTwice() throws Exception {
		String key = UUID.randomUUID().toString();
		String first = practice(guardianToken, link.getId(), signal.getId(), key)
				.andReturn().getResponse().getContentAsString();
		String retried = practice(guardianToken, link.getId(), signal.getId(), key)
				.andReturn().getResponse().getContentAsString();

		assertThat(retried).isEqualTo(first);
		assertThat(pushSender.sent()).hasSize(1);
	}

	@Test
	void theRecipientCannotPractice() throws Exception {
		practice(jwtTokenProvider.createAccessToken(recipient.getId(), "RECIPIENT"), link.getId(), signal.getId(),
				UUID.randomUUID().toString())
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("ROLE_MISMATCH"));
	}

	@Test
	void practicingOnAnEndedLinkIsRejected() throws Exception {
		link.end(LocalDateTime.now());
		linkRepository.saveAndFlush(link);

		practice(guardianToken, link.getId(), signal.getId(), UUID.randomUUID().toString())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("LINK_ENDED"));
	}
}
