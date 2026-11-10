package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sonulim.domain.signal.domain.FavoriteSignal;
import com.sonulim.domain.signal.domain.FavoriteSignalRepository;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자주 쓰는 신호 읽기 — 다른 도메인(link의 홈 요약 S-4, #81)이 부르는 창구. 지정한 순서 그대로, 그 사이 삭제된
 * 신호는 뺀다. 연결 접근·역할 검사는 부르는 쪽이 이미 했다고 보고 여기서는 하지 않는다.
 */
@Service
public class FavoriteSignalQueryService {

	private final FavoriteSignalRepository favoriteSignalRepository;
	private final SignalRepository signalRepository;

	public FavoriteSignalQueryService(FavoriteSignalRepository favoriteSignalRepository,
			SignalRepository signalRepository) {
		this.favoriteSignalRepository = favoriteSignalRepository;
		this.signalRepository = signalRepository;
	}

	@Transactional(readOnly = true)
	public List<Signal> list(UUID linkId) {
		Map<UUID, Signal> activeSignals = signalRepository.findByLinkIdAndDeletedAtIsNull(linkId).stream()
				.collect(Collectors.toMap(Signal::getId, Function.identity()));
		return favoriteSignalRepository.findByLinkIdOrderByPosition(linkId).stream()
				.map(FavoriteSignal::getSignalId)
				.map(activeSignals::get)
				.filter(Objects::nonNull)
				.toList();
	}
}
