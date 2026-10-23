package com.pg_management.security;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "owner_otp_challenges")
public class OwnerOtpChallenge {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String whatsappNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OwnerOtpPurpose purpose;

	@Column(nullable = false, length = 100)
	private String codeHash;

	@Column(length = 64)
	private String requesterKey;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int failedAttempts;

	protected OwnerOtpChallenge() {
	}

	public OwnerOtpChallenge(
			String whatsappNumber,
			OwnerOtpPurpose purpose,
			String codeHash,
			String requesterKey,
			Instant createdAt,
			Instant expiresAt) {
		this.whatsappNumber = whatsappNumber;
		this.purpose = purpose;
		this.codeHash = codeHash;
		this.requesterKey = requesterKey;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public String getWhatsappNumber() {
		return whatsappNumber;
	}

	public OwnerOtpPurpose getPurpose() {
		return purpose;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public String getRequesterKey() {
		return requesterKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public int getFailedAttempts() {
		return failedAttempts;
	}

	public void recordFailedAttempt() {
		failedAttempts++;
	}
}
