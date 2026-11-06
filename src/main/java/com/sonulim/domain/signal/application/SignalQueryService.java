package com.sonulim.domain.signal.application;

import java.util.List;
import java.util.UUID;

import com.sonulim.domain.signal.domain.Signal;
import com.sonulim.domain.signal.domain.SignalRepository;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import com.sonulim.global.linkaccess.LinkAccessGuard;
import org.springframework.stereotype.Service;

@Service
public class SignalQueryService {

	private final SignalRepository signalRepository;
	private final LinkAccessGuard linkAccessGuard;

	public SignalQueryService(SignalRepository signalRepository, LinkAccessGuard linkAccessGuard) {
		this.signalRepository = signalRepository;
		this.linkAccessGuard = linkAccessGuard;
	}

	public List<Signal> list(UUID linkId, UUID requesterId) {
		linkAccessGuard.verifyAccessible(linkId, requesterId);
		return signalRepository.findByLinkIdAndDeletedAtIsNull(linkId);
	}

	/**
	 * signalId로 먼저 찾고, 그 신호의 실제 linkId를 기준으로 접근 권한을 검증한다(경로의
	 * linkId가 아니라). 그래서 접근 권한이 있어도 경로의 linkId가 신호의 실제 linkId와
	 * 다르면 다른 연결의 신호를 가리키는 거라 똑같이 404로 처리한다.
	 */
	public Signal getDetail(UUID pathLinkId, UUID signalId, UUID requesterId) {
		Signal signal = signalRepository.findByIdAndDeletedAtIsNull(signalId)
				.orElseThrow(() -> new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요"));

		linkAccessGuard.verifyAccessible(signal.getLinkId(), requesterId);

		if (!signal.getLinkId().equals(pathLinkId)) {
			throw new SonulimException(ErrorCode.NOT_FOUND, "신호를 찾을 수 없어요");
		}

		return signal;
	}
}
