package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 직접 만들기의 리듬 단계에서 고를 수 있는 프리셋 — "서버 내부 처리" 문서의 rhythm_presets
 * 테이블(id, rhythm). SignalTemplate과 같은 패턴: 전역 시드 카탈로그라 linkId가 없다.
 */
@Entity
@Table(name = "rhythm_presets")
public class RhythmPreset {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Convert(converter = RhythmListConverter.class)
	@Column(nullable = false)
	private List<RhythmBeat> rhythm;

	protected RhythmPreset() {
	}

	public RhythmPreset(List<RhythmBeat> rhythm) {
		this.rhythm = List.copyOf(rhythm);
	}

	public UUID getId() {
		return id;
	}

	public List<RhythmBeat> getRhythm() {
		return rhythm;
	}
}
