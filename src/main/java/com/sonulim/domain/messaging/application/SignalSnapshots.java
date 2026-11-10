package com.sonulim.domain.messaging.application;

import java.util.List;

import com.sonulim.domain.messaging.domain.SignalCategory;
import com.sonulim.domain.messaging.domain.SignalSnapshot;
import com.sonulim.domain.signal.domain.RhythmBeat;
import com.sonulim.domain.signal.domain.Signal;

/**
 * signal 도메인의 신호를 보낸 시점 값으로 굳힌다. 신호를 고치거나 지워도 과거 기록은 그대로다(공통 데이터 형식).
 */
final class SignalSnapshots {

	private SignalSnapshots() {
	}

	static SignalSnapshot of(Signal signal) {
		return new SignalSnapshot(signal.getId(), signal.getName(), signal.getDescription(),
				SignalCategory.valueOf(signal.getCategory().name()), signal.getIcon(),
				rhythm(signal), signal.getPulses(), (int) signal.calculateDurationMs());
	}

	/**
	 * 기본 신호는 불규칙한 쉼이 있어 박자 종류로 담기지 않는다 — 그때는 null이고 기기는 pulses로 울린다.
	 */
	private static List<String> rhythm(Signal signal) {
		List<RhythmBeat> beats = signal.getRhythm();
		return beats == null ? null : beats.stream().map(Enum::name).toList();
	}
}
