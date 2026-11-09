package com.sonulim.domain.signal.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.sonulim.domain.signal.domain.FavoriteSignal;
import com.sonulim.domain.signal.domain.FavoriteSignalRepository;
import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S-4 자주 쓰는 신호 지정 — PUT 통째로 교체. favorite_signals는 Signal과 별도
 * 테이블(link_id, signal_id, position)이라 Signal 자체는 건드리지 않는다.
 */
@Service
public class FavoriteSignalCommandService {

	private static final int MAX_FAVORITES = 6;

	private final FavoriteSignalRepository favoriteSignalRepository;
	private final SignalRepository signalRepository;
	private final LinkAccessGuard linkAccessGuard;

	public FavoriteSignalCommandService(FavoriteSignalRepository favoriteSignalRepository,
			SignalRepository signalRepository, LinkAccessGuard linkAccessGuard) {
		this.favoriteSignalRepository = favoriteSignalRepository;
		this.signalRepository = signalRepository;
		this.linkAccessGuard = linkAccessGuard;
	}

	@Transactional
	public List<Signal> replace(UUID linkId, UUID requesterId, String role, List<UUID> signalIds) {
		GuardianOnly.require(role);
		linkAccessGuard.verifyAccessible(linkId, requesterId);

		if (signalIds.size() > MAX_FAVORITES) {
			throw SonulimException.forField(ErrorCode.FAVORITE_LIMIT_EXCEEDED, "자주 쓰는 신호는 6개까지 고를 수 있어요",
					"signalIds");
		}
		Set<UUID> uniqueIds = new HashSet<>(signalIds);
		if (uniqueIds.size() != signalIds.size()) {
			throw SonulimException.forField(ErrorCode.VALIDATION_FAILED, "같은 신호를 두 번 넣을 수 없어요", "signalIds");
		}

		Map<UUID, Signal> activeSignals = signalRepository.findByLinkIdAndDeletedAtIsNull(linkId).stream()
				.collect(Collectors.toMap(Signal::getId, signal -> signal));
		List<Signal> target = new ArrayList<>();
		for (UUID signalId : signalIds) {
			Signal signal = activeSignals.get(signalId);
			if (signal == null) {
				throw new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요");
			}
			target.add(signal);
		}

		// Hibernate는 같은 트랜잭션 안에서 삭제보다 삽입을 먼저 flush하므로, 바로 deleteByLinkId
		// 뒤에 flush하지 않으면 재지정 시 (link_id, signal_id) 유니크 제약과 충돌한다.
		favoriteSignalRepository.deleteByLinkId(linkId);
		favoriteSignalRepository.flush();
		for (int position = 0; position < target.size(); position++) {
			favoriteSignalRepository.save(new FavoriteSignal(linkId, target.get(position).getId(), position));
		}
		return target;
	}
}
