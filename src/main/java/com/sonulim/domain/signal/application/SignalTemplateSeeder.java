package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import com.sonulim.global.signalseed.DefaultSignalSeeder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * link 도메인이 연결 승인 시 호출하는 DefaultSignalSeeder의 signal 쪽 구현.
 * signal_templates 카탈로그를 이 연결의 Signal로 복사한다. 카탈로그 내용(개수·구성)은
 * 아직 확정되지 않아 시드 데이터가 비어 있을 수 있고, 그 경우 아무 것도 만들지 않는다
 * — 비어 있을 때 아무 일도 안 하는 것과 "아무 것도 안 하는 구현"은 다르다: 카탈로그가
 * 채워지면 이 로직 그대로 동작한다.
 */
@Service
public class SignalTemplateSeeder implements DefaultSignalSeeder {

	private final SignalTemplateRepository signalTemplateRepository;
	private final SignalRepository signalRepository;

	public SignalTemplateSeeder(SignalTemplateRepository signalTemplateRepository, SignalRepository signalRepository) {
		this.signalTemplateRepository = signalTemplateRepository;
		this.signalRepository = signalRepository;
	}

	@Override
	@Transactional
	public void seedDefaults(UUID linkId) {
		List<SignalTemplate> templates = signalTemplateRepository.findAll();
		for (SignalTemplate template : templates) {
			if (signalRepository.existsByLinkIdAndTemplateId(linkId, template.getId())) {
				continue;
			}
			ensureRhythmNotDuplicated(linkId, template);
			Signal signal = Signal.fromTemplate(linkId, template.getId(), template.getName(),
					template.getDescription(), template.getCategory(), template.getIcon(), template.getRhythm());
			signalRepository.save(signal);
		}
	}

	/**
	 * 새로 승인된 연결은 이 시점에 신호가 하나도 없으므로, 이 충돌은 사실상 signal_templates
	 * 카탈로그 자체에 같은 리듬의 템플릿이 두 개 있다는 뜻이다 — 보호자가 유발한 문제가
	 * 아니라 우리 쪽 데이터 버그다. 그래서 RHYTHM_DUPLICATE(보호자용 비즈니스 에러)로
	 * 노출하지 않고 처리하지 못한 예외로 던져서 GlobalExceptionHandler가 500 내부 오류로
	 * 바꾸고 로그를 남기게 한다(은솔님 PR #52 리뷰). 여러 템플릿을 한 번에 넣는 도중
	 * 충돌이 나면 @Transactional이 그 호출에서 만든 것 전부 롤백한다.
	 */
	private void ensureRhythmNotDuplicated(UUID linkId, SignalTemplate template) {
		if (signalRepository.findByLinkIdAndRhythm(linkId, template.getRhythm(), null).isPresent()) {
			throw new IllegalStateException(
					"signal_templates 카탈로그에 같은 리듬의 템플릿이 있어요: " + template.getId());
		}
	}
}
