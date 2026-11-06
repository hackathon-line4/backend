package com.sonulim.global;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Swagger의 Authorize 버튼에 액세스 토큰을 넣을 수 있게 한다. 인증이 필요한 API는
 * {@code @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)}를 붙인다.
 */
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
		bearerFormat = "JWT")
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";
}
