package com.sonulim.domain.signal.application;

import java.util.List;

import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * signal_templates 카탈로그의 MVP 기본 신호 6종을 앱 시작 시 한 번만 채운다(이미 있으면
 * 아무 것도 하지 않음). 값은 촉신호 -> 진동 변환 규칙 문서(기획 확인)의 실제 동작 분석
 * 결과를 그대로 쓴다 — 쉼이 불규칙(150/500/50ms)해서 rhythm(SHORT/MEDIUM/LONG)으로는
 * 표현이 안 돼 rhythm은 null, pulses만 채운다. description·icon은 기획/디자인에서
 * 아직 확정되지 않아 null로 둔다(docs/domains/signal.md Pending Decisions).
 */
@Component
public class SignalTemplateCatalogSeeder implements ApplicationRunner {

	private final SignalTemplateRepository signalTemplateRepository;

	public SignalTemplateCatalogSeeder(SignalTemplateRepository signalTemplateRepository) {
		this.signalTemplateRepository = signalTemplateRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (signalTemplateRepository.count() > 0) {
			return;
		}
		signalTemplateRepository.saveAll(List.of(
				new SignalTemplate("나 여기 있어", null, SignalCategory.DAILY, null, null,
						List.of(150, 150, 150)),
				new SignalTemplate("밥 먹자", null, SignalCategory.DAILY, null, null,
						List.of(700)),
				new SignalTemplate("가자", null, SignalCategory.OUTING, null, null,
						List.of(150, 150, 150, 150, 150, 150, 150)),
				new SignalTemplate("기다려", null, SignalCategory.OUTING, null, null,
						List.of(350, 50, 350, 50, 350, 50, 350)),
				new SignalTemplate("누가 왔어", null, SignalCategory.OUTING, null, null,
						List.of(150, 150, 150, 150, 150, 150, 150, 500, 700)),
				new SignalTemplate("위험해", null, SignalCategory.EMERGENCY, null, null,
						List.of(1000, 150, 1000, 500, 1000, 150, 1000, 500, 1000, 150, 1000))));
	}
}
