package com.sonulim.domain.account.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, UUID> {
}
