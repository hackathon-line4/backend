package com.sonulim.domain.signal.domain;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서비스가 제공하는 기본 신호(현장 촉신호 어휘) 시드 데이터 — "서버 내부 처리" 문서의
 * signal_templates 테이블. 연결마다 독립적인 Signal과 달리 전역 카탈로그라 linkId가 없다.
 */
@Entity
@Table(name = "signal_templates")
public class SignalTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 12)
	private String name;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SignalCategory category;

	private String icon;

	@Convert(converter = RhythmListConverter.class)
	@Column(nullable = false)
	private List<RhythmBeat> rhythm;

	protected SignalTemplate() {
	}

	public SignalTemplate(String name, String description, SignalCategory category, String icon,
			List<RhythmBeat> rhythm) {
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.rhythm = List.copyOf(rhythm);
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public SignalCategory getCategory() {
		return category;
	}

	public String getIcon() {
		return icon;
	}

	public List<RhythmBeat> getRhythm() {
		return rhythm;
	}
}
