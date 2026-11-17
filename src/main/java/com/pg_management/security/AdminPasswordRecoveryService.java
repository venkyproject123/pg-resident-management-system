package com.pg_management.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

@Service
public class AdminPasswordRecoveryService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminPasswordRecoveryService.class);
	private static final Duration RECOVERY_ID_LIFETIME = Duration.ofHours(24);
	private static final char[] RECOVERY_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final AdminCredentialRepository credentialRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();
	private final String configuredEmail;
	private final String configuredPassword;

	public AdminPasswordRecoveryService(
			AdminCredentialRepository credentialRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.admin.email:}") String configuredEmail,
			@Value("${app.admin.password:}") String configuredPassword) {
		this.credentialRepository = credentialRepository;
		this.passwordEncoder = passwordEncoder;
		this.configuredEmail = normalizeEmail(configuredEmail);
		this.configuredPassword = configuredPassword;
	}

	@PostConstruct
	@Transactional
	public void initializeConfiguredAdmin() {
		if (configuredEmail.isBlank() && configuredPassword.isBlank()) {
			return;
		}
		if (configuredEmail.isBlank() || configuredPassword.isBlank()) {
			throw new IllegalStateException("Set both ADMIN_EMAIL and ADMIN_PASSWORD to configure the admin account");
		}
		credentialRepository.findById(configuredEmail).orElseGet(() ->
				credentialRepository.saveAndFlush(new AdminCredential(
						configuredEmail, passwordEncoder.encode(configuredPassword))));
	}

	@Transactional
	public String generateRecoveryId(String email) {
		AdminCredential credential = credentialRepository.findById(normalizeEmail(email)).orElse(null);
		if (credential == null) {
			return null;
		}
		if (credential.getRecoveryIdHash() != null
				&& credential.getRecoveryIdExpiresAt() != null
				&& credential.getRecoveryIdExpiresAt().isAfter(Instant.now())) {
			return null;
		}

		String recoveryId = generateRandomId();
		credential.setRecoveryId(
				hashRecoveryId(normalizeRecoveryId(recoveryId)), Instant.now().plus(RECOVERY_ID_LIFETIME));
		credentialRepository.save(credential);
		LOGGER.warn("Admin password recovery ID generated; retrieve it from protected backend logs: {}", recoveryId);
		return recoveryId;
	}

	@Transactional
	public void resetPassword(String email, String recoveryId, String newPassword) {
		AdminCredential credential = credentialRepository.findById(normalizeEmail(email))
				.orElseThrow(this::invalidRecoveryId);
		String suppliedHash = hashRecoveryId(normalizeRecoveryId(recoveryId));
		if (credential.getRecoveryIdHash() == null
				|| !MessageDigest.isEqual(
						credential.getRecoveryIdHash().getBytes(StandardCharsets.US_ASCII),
						suppliedHash.getBytes(StandardCharsets.US_ASCII))
				|| credential.getRecoveryIdExpiresAt() == null
				|| credential.getRecoveryIdExpiresAt().isBefore(Instant.now())) {
			throw invalidRecoveryId();
		}

		credential.changePassword(passwordEncoder.encode(newPassword));
		credentialRepository.save(credential);
	}

	private String generateRandomId() {
		StringBuilder value = new StringBuilder(19);
		for (int index = 0; index < 16; index++) {
			if (index > 0 && index % 4 == 0) {
				value.append('-');
			}
			value.append(RECOVERY_ID_ALPHABET[secureRandom.nextInt(RECOVERY_ID_ALPHABET.length)]);
		}
		return value.toString();
	}

	private String normalizeRecoveryId(String recoveryId) {
		return recoveryId.replace("-", "").trim().toUpperCase(Locale.ROOT);
	}

	private String hashRecoveryId(String recoveryId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(recoveryId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private ResponseStatusException invalidRecoveryId() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The recovery ID is invalid or expired");
	}
}
