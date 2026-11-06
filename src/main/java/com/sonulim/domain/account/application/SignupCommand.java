package com.sonulim.domain.account.application;

public record SignupCommand(String email, String password, String name, boolean termsAgreed, boolean privacyAgreed) {
}
