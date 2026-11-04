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
 * {@code @EnableJpaAuditing}은 이 테스트 안에서만 켠다 — 실제 앱에는 아직 managed entity가
 * 하나도 없어서(BaseEntity는 @MappedSuperclass일 뿐) 전역으로 켜면 "JPA metamodel must not be
 * empty"로 부팅이 실패한다. 첫 실제 도메인 @Entity를 만들 때 그 사람이 global에 똑같은
 * {@code @EnableJpaAuditing} 설정 클래스를 추가한다(docs/conventions.md 참고).
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
