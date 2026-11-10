package com.sonulim.domain.signal.application;

import com.sonulim.domain.signal.domain.SignalTemplate;

/**
 * alreadyAdded는 SignalTemplate 자체의 속성이 아니라 "어느 연결에서 보는지"에 따라
 * 달라지는 조회 시점 값이라 도메인 객체에 넣지 않고 별도로 묶어서 돌려준다.
 */
public record SignalTemplateView(SignalTemplate template, boolean alreadyAdded) {
}
