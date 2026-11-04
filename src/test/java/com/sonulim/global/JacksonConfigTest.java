package com.sonulim.global;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

	@Test
	void applicationObjectMapperSerializesLocalDateTimeWithSeoulOffset() throws Exception {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.register(JacksonConfig.class);
			context.refresh();

			Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
			context.getBeansOfType(org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer.class)
					.values()
					.forEach(customizer -> customizer.customize(builder));
			ObjectMapper objectMapper = builder.build();

			String json = objectMapper.writeValueAsString(LocalDateTime.of(2026, 11, 4, 8, 55, 2));

			assertThat(json).isEqualTo("\"2026-11-04T17:55:02+09:00\"");
		}
	}
}
