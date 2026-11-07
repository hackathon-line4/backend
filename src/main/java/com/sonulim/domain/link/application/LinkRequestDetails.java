package com.sonulim.domain.link.application;

import com.sonulim.domain.account.domain.User;
import com.sonulim.domain.link.domain.Link;
import com.sonulim.domain.link.domain.LinkRequest;

/**
 * 연결 요청 응답에 필요한 것: 요청, 보호자·당사자, 승인됐으면 만들어진 연결(아니면 null).
 */
public record LinkRequestDetails(LinkRequest request, User guardian, User recipient, Link link) {
}
