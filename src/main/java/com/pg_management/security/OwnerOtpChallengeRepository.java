package com.pg_management.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerOtpChallengeRepository extends JpaRepository<OwnerOtpChallenge, Long> {

	Optional<OwnerOtpChallenge> findFirstByWhatsappNumberAndPurposeOrderByCreatedAtDesc(
			String whatsappNumber, OwnerOtpPurpose purpose);

	long countByWhatsappNumberAndPurposeAndCreatedAtAfter(
			String whatsappNumber, OwnerOtpPurpose purpose, Instant after);

	long countByRequesterKeyAndCreatedAtAfter(String requesterKey, Instant after);

	void deleteByWhatsappNumberAndPurpose(String whatsappNumber, OwnerOtpPurpose purpose);

	void deleteByExpiresAtBefore(java.time.Instant expiresAt);
}
