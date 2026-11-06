package com.sonulim.domain.signal.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.sonulim.global.BaseEntity;
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
 * link 도메인이 아직 없어서 Link 엔티티를 참조하지 않고 linkId만 평범한 컬럼으로 가진다(FK 없음).
 * 통합 시점에 참조 무결성·연결 삭제 시 처리 방식을 link 도메인과 함께 정한다.
 * ID는 account 도메인과 같은 관례(UUID, Hibernate 생성)를 따른다.
 */
@Entity
@Table(name = "signals")
public class Signal extends BaseEntity {

	private static final long GAP_MS = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "link_id", nullable = false)
	private UUID linkId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SignalSource source;

	@Column(name = "template_id")
	private UUID templateId;

	@Column(name = "preset_id")
	private UUID presetId;

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

	@Column(name = "practiced_at")
	private LocalDateTime practicedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	protected Signal() {
	}

	private Signal(UUID linkId, SignalSource source, UUID templateId, UUID presetId, String name,
			String description, SignalCategory category, String icon, List<RhythmBeat> rhythm) {
		this.linkId = linkId;
		this.source = source;
		this.templateId = templateId;
		this.presetId = presetId;
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.rhythm = List.copyOf(rhythm);
	}

	public static Signal createCustom(UUID linkId, String name, String description, SignalCategory category,
			String icon, List<RhythmBeat> rhythm) {
		return new Signal(linkId, SignalSource.CUSTOM, null, null, name, description, category, icon, rhythm);
	}

	public void softDelete() {
		this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
	}

	public boolean isPracticed() {
		return practicedAt != null;
	}

	/**
	 * 공통 데이터 형식 문서 기준: 박자 길이 합 + 박자 사이 간격(300ms) * (박자 수 - 1).
	 * 마지막 박자 뒤에는 간격을 더하지 않는다 — 예시(SHORT,SHORT,LONG -> 1900ms)로 검증됨.
	 */
	public long calculateDurationMs() {
		long beatsTotal = rhythm.stream().mapToLong(RhythmBeat::getDurationMs).sum();
		long gaps = (long) (rhythm.size() - 1) * GAP_MS;
		return beatsTotal + gaps;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLinkId() {
		return linkId;
	}

	public SignalSource getSource() {
		return source;
	}

	public UUID getTemplateId() {
		return templateId;
	}

	public UUID getPresetId() {
		return presetId;
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

	public LocalDateTime getPracticedAt() {
		return practicedAt;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}
}
