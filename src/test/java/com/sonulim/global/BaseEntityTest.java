package com.sonulim.global;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest}는 일반 @Configuration(global.JpaAuditingConfig)을 불러오지 않으므로
 * 이 테스트 안에서 {@code @EnableJpaAuditing}을 따로 켠다.
 */
@DataJpaTest
@Import(BaseEntityTest.AuditingTestConfig.class)
class BaseEntityTest {

	@TestConfiguration
	@EnableJpaAuditing
	static class AuditingTestConfig {
	}

	@Entity
	static class TestEntity extends BaseEntity {
		@Id
		@GeneratedValue
		Long id;

		String name = "initial";
	}

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void setsCreatedAtAndUpdatedAtOnPersist() {
		TestEntity saved = entityManager.persistFlushFind(new TestEntity());

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void updatesUpdatedAtButKeepsCreatedAtOnModification() {
		TestEntity saved = entityManager.persistFlushFind(new TestEntity());
		var originalCreatedAt = saved.getCreatedAt();

		saved.name = "changed";
		entityManager.flush();
		entityManager.clear();
		TestEntity reloaded = entityManager.find(TestEntity.class, saved.id);

		assertThat(reloaded.getCreatedAt()).isEqualTo(originalCreatedAt);
		assertThat(reloaded.getUpdatedAt()).isAfterOrEqualTo(originalCreatedAt);
	}
}
