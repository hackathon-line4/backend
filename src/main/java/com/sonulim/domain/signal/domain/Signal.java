package com.sonulim.domain.signal.domain;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.sonulim.global.BaseEntity;
import com.sonulim.global.exception.ErrorCode;
import com.sonulim.global.exception.SonulimException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * link 도메인이 아직 없어서 Link 엔티티를 참조하지 않고 linkId만 평범한 컬럼으로 가진다(FK 없음).
 * 통합 시점에 참조 무결성·연결 삭제 시 처리 방식을 link 도메인과 함께 정한다.
 * ID는 account 도메인과 같은 관례(UUID, Hibernate 생성)를 따른다.
 */
@Entity
@Table(name = "signals", uniqueConstraints = @UniqueConstraint(name = "uk_signals_link_idempotency_key",
		columnNames = {"link_id", "idempotency_key"}))
public class Signal extends BaseEntity {

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
	private List<RhythmBeat> rhythm;

	@Convert(converter = PulseListConverter.class)
	@Column(nullable = false)
	private List<Integer> pulses;

	@Column(name = "practiced_at")
	private LocalDateTime practicedAt;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "idempotency_key")
	private UUID idempotencyKey;

	protected Signal() {
	}

	private Signal(UUID linkId, SignalSource source, UUID templateId, UUID presetId, String name,
			String description, SignalCategory category, String icon, List<RhythmBeat> rhythm,
			List<Integer> pulses) {
		this.linkId = linkId;
		this.source = source;
		this.templateId = templateId;
		this.presetId = presetId;
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.rhythm = rhythm == null ? null : List.copyOf(rhythm);
		this.pulses = List.copyOf(pulses);
	}

	public static Signal createCustom(UUID linkId, String name, String description, SignalCategory category,
			String icon, List<RhythmBeat> rhythm) {
		return new Signal(linkId, SignalSource.CUSTOM, null, null, name, description, category, icon, rhythm,
				RhythmBeat.toPulses(rhythm));
	}

	/**
	 * 기본 신호는 rhythm이 불규칙한 쉼을 가질 수 있어 SHORT/MEDIUM/LONG으로 못 담을 때가
	 * 있다(촉신호 -> 진동 변환 규칙 문서) — 그래서 rhythm은 null일 수 있고, pulses를 템플릿이
	 * 가진 값 그대로 복사한다.
	 */
	public static Signal fromTemplate(UUID linkId, UUID templateId, String name, String description,
			SignalCategory category, String icon, List<RhythmBeat> rhythm, List<Integer> pulses) {
		return new Signal(linkId, SignalSource.TEMPLATE, templateId, null, name, description, category, icon, rhythm,
				pulses);
	}

	public void softDelete() {
		this.deletedAt = LocalDateTime.now(ZoneOffset.UTC);
	}

	/**
	 * POST /signals의 Idempotency-Key 재시도 판별용. 생성 팩토리 메서드 시그니처를 건드리지
	 * 않으려고 별도 메서드로 둔다 — 호출 측(SignalCommandService)만 이 값을 채운다.
	 */
	public void stampIdempotencyKey(UUID idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public boolean isPracticed() {
		return practicedAt != null;
	}

	/**
	 * 기본 신호(SignalSource.TEMPLATE)는 이름·리듬을 바꿀 수 없다(SIGNAL_NOT_EDITABLE,
	 * 에러 코드 설명 그대로). 설명·카테고리·아이콘은 기본 신호도 바꿀 수 있다.
	 */
	public void updateName(String name) {
		requireEditable();
		this.name = name;
	}

	public void updateRhythm(List<RhythmBeat> rhythm) {
		requireEditable();
		this.rhythm = List.copyOf(rhythm);
		this.pulses = RhythmBeat.toPulses(rhythm);
	}

	public void updateDescription(String description) {
		this.description = description;
	}

	public void updateCategory(SignalCategory category) {
		this.category = category;
	}

	public void updateIcon(String icon) {
		this.icon = icon;
	}

	private void requireEditable() {
		if (source == SignalSource.TEMPLATE) {
			throw new SonulimException(ErrorCode.SIGNAL_NOT_EDITABLE, "기본 신호는 이름·리듬을 바꿀 수 없어요");
		}
	}

	/**
	 * durationMs는 pulses 배열의 합이다(촉신호 -> 진동 변환 규칙 문서).
	 */
	public long calculateDurationMs() {
		return pulses.stream().mapToLong(Integer::longValue).sum();
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

	public List<Integer> getPulses() {
		return pulses;
	}

	public LocalDateTime getPracticedAt() {
		return practicedAt;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public UUID getIdempotencyKey() {
		return idempotencyKey;
	}
}
