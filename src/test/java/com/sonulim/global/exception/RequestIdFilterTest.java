package com.sonulim.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class RequestIdFilterTest {

	@RestController
	static class OkController {
		@GetMapping("/test/ok")
		String ok() {
			return "ok";
		}
	}

	@Test
	void addsAnXRequestIdHeaderToEveryResponse() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OkController())
				.addFilter(new RequestIdFilter())
				.build();

		mockMvc.perform(get("/test/ok"))
				.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void usesADifferentRequestIdPerRequest() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new OkController())
				.addFilter(new RequestIdFilter())
				.build();

		String first = mockMvc.perform(get("/test/ok")).andReturn().getResponse().getHeader("X-Request-Id");
		String second = mockMvc.perform(get("/test/ok")).andReturn().getResponse().getHeader("X-Request-Id");

		org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo(second);
	}
}
