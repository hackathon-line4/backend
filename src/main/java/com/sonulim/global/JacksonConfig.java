package com.sonulim.global;

import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

@Configuration
public class JacksonConfig {

	@Bean
	public Jackson2ObjectMapperBuilderCustomizer utcToSeoulDateTimeCustomizer() {
		return builder -> builder.serializerByType(LocalDateTime.class, new UtcToSeoulLocalDateTimeSerializer());
	}
}
