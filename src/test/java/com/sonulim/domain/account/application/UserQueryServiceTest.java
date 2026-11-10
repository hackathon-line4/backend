package com.sonulim.domain.account.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.sonulim.domain.account.domain.Role;
import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.account.domain.UserRepository;

@SpringBootTest
@Transactional
class UserQueryServiceTest {

	@Autowired
	private UserQueryService userQueryService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void findReturnsNameAndRole() {
		User user = save("guardian@example.com", "이영희");
		user.selectRole(Role.GUARDIAN, LocalDateTime.now());

		assertThat(userQueryService.find(user.getId()))
				.contains(new UserSummary(user.getId(), "이영희", Role.GUARDIAN));
	}

	@Test
	void findReturnsEmptyForUnknownUser() {
		assertThat(userQueryService.find(UUID.randomUUID())).isEmpty();
	}

	@Test
	void findAllReturnsOnlyExistingUsersKeyedById() {
		User guardian = save("a@example.com", "이영희");
		User recipient = save("b@example.com", "김하늘");

		Map<UUID, UserSummary> result =
				userQueryService.findAll(List.of(guardian.getId(), recipient.getId(), UUID.randomUUID()));

		assertThat(result).hasSize(2);
		assertThat(result.get(guardian.getId()).name()).isEqualTo("이영희");
		assertThat(result.get(recipient.getId()).name()).isEqualTo("김하늘");
	}

	@Test
	void findAllWithEmptyIdsReturnsEmptyMap() {
		assertThat(userQueryService.findAll(List.of())).isEmpty();
	}

	private User save(String email, String name) {
		return userRepository.saveAndFlush(User.register(email, "hash", name));
	}
}
