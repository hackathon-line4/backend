package com.sonulim.domain.signal.application;

import com.sonulim.domain.signal.domain.RhythmPreset;
import com.sonulim.domain.signal.domain.Signal;

/**
 * usedBy는 RhythmPreset 자체의 속성이 아니라 "어느 연결에서 보는지"에 따라 달라지는 조회
 * 시점 값이라 도메인 객체에 넣지 않고 별도로 묶어서 돌려준다. usedBy가 null이면 사용 중인
 * 신호가 없다는 뜻.
 */
public record RhythmPresetView(RhythmPreset preset, Signal usedBy) {
}
