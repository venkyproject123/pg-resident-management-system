package com.pg_management.security;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_credentials")
public class AdminCredential {

	@Id
	@Column(length = 100)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "recovery_id_hash", length = 64)
	private String recoveryIdHash;

	@Column(name = "recovery_id_expires_at")
	private Instant recoveryIdExpiresAt;

	protected AdminCredential() {
	}

	public AdminCredential(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getRecoveryIdHash() {
		return recoveryIdHash;
	}

	public Instant getRecoveryIdExpiresAt() {
		return recoveryIdExpiresAt;
	}

	public void setRecoveryId(String recoveryIdHash, Instant expiresAt) {
		this.recoveryIdHash = recoveryIdHash;
		this.recoveryIdExpiresAt = expiresAt;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.recoveryIdHash = null;
		this.recoveryIdExpiresAt = null;
	}
}
