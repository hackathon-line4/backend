package com.sonulim.domain.signal.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.sonulim.domain.signal.domain.SignalCategory;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.domain.signal.domain.SignalTemplate;
import com.sonulim.domain.signal.domain.SignalTemplateRepository;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.springframework.stereotype.Service;

/**
 * M-2 "기본 신호 목록" — GET /api/v1/signal-templates.
 */
@Service
public class SignalTemplateQueryService {

	private final SignalTemplateRepository signalTemplateRepository;
	private final SignalRepository signalRepository;
	private final LinkAccessGuard linkAccessGuard;

	public SignalTemplateQueryService(SignalTemplateRepository signalTemplateRepository,
			SignalRepository signalRepository, LinkAccessGuard linkAccessGuard) {
		this.signalTemplateRepository = signalTemplateRepository;
		this.signalRepository = signalRepository;
		this.linkAccessGuard = linkAccessGuard;
	}

	/**
	 * linkId가 있으면 결과를 만들기 전에 접근을 검증한다(검색 결과가 비어 있어도 수행) —
	 * alreadyAdded가 그 연결의 정보를 드러내기 때문이다. q는 이름·뜻 부분 일치(대소문자 무시).
	 * 정렬(이름순)은 명세에 없는 구현 편의상 선택이다.
	 */
	public List<SignalTemplateView> list(UUID requesterId, String role, UUID linkId, String q,
			SignalCategory category) {
		GuardianOnly.require(role);
		if (linkId != null) {
			linkAccessGuard.verifyAccessible(linkId, requesterId);
		}

		String normalizedQuery = q == null ? null : q.toLowerCase(Locale.ROOT);
		return signalTemplateRepository.findAll().stream()
				.filter(template -> category == null || template.getCategory() == category)
				.filter(template -> matchesQuery(template, normalizedQuery))
				.sorted(Comparator.comparing(SignalTemplate::getName))
				.map(template -> new SignalTemplateView(template, isAlreadyAdded(linkId, template)))
				.toList();
	}

	private boolean matchesQuery(SignalTemplate template, String normalizedQuery) {
		if (normalizedQuery == null) {
			return true;
		}
		boolean nameMatches = template.getName().toLowerCase(Locale.ROOT).contains(normalizedQuery);
		boolean descriptionMatches = template.getDescription() != null
				&& template.getDescription().toLowerCase(Locale.ROOT).contains(normalizedQuery);
		return nameMatches || descriptionMatches;
	}

	private boolean isAlreadyAdded(UUID linkId, SignalTemplate template) {
		return linkId != null
				&& signalRepository.existsByLinkIdAndTemplateIdAndDeletedAtIsNull(linkId, template.getId());
	}
}
