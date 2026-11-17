package com.pg_management.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OwnerPasswordRecoveryService {

	private static final Duration RECOVERY_ID_LIFETIME = Duration.ofHours(24);
	private static final char[] RECOVERY_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

	private final OwnerAccountRepository ownerAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final SecureRandom secureRandom = new SecureRandom();

	public OwnerPasswordRecoveryService(
			OwnerAccountRepository ownerAccountRepository,
			PasswordEncoder passwordEncoder) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public String generateRecoveryId(Long ownerId) {
		OwnerAccount owner = ownerAccountRepository.findById(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
		if (owner.isRemoved()) {
			throw new ResponseStatusException(HttpStatus.GONE, "This owner has been removed");
		}
		if (!owner.isApproved()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Owner registration must be approved first");
		}

		String recoveryId;
		String recoveryIdHash;
		do {
			recoveryId = generateRandomId();
			recoveryIdHash = hashRecoveryId(recoveryId);
		} while (ownerAccountRepository.existsByRecoveryIdHash(recoveryIdHash));

		owner.setRecoveryId(recoveryIdHash, Instant.now().plus(RECOVERY_ID_LIFETIME));
		ownerAccountRepository.save(owner);
		return recoveryId;
	}

	@Transactional
	public void resetPassword(String recoveryId, String newPassword) {
		String normalizedId = normalizeRecoveryId(recoveryId);
		OwnerAccount owner = ownerAccountRepository.findByRecoveryIdHashForUpdate(hashRecoveryId(normalizedId))
				.orElseThrow(this::invalidRecoveryId);

		if (owner.isRemoved()
				|| owner.getRecoveryIdExpiresAt() == null
				|| owner.getRecoveryIdExpiresAt().isBefore(Instant.now())) {
			throw invalidRecoveryId();
		}

		if (!owner.isApproved()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This owner account is not approved yet");
		}

		owner.changePassword(passwordEncoder.encode(newPassword));
		owner.clearRecoveryId();
		ownerAccountRepository.save(owner);
	}

	private String generateRandomId() {
		StringBuilder value = new StringBuilder(16);
		for (int index = 0; index < 16; index++) {
			value.append(RECOVERY_ID_ALPHABET[secureRandom.nextInt(RECOVERY_ID_ALPHABET.length)]);
		}
		return value.toString();
	}

	private String normalizeRecoveryId(String recoveryId) {
		return recoveryId.replace("-", "").trim().toUpperCase(java.util.Locale.ROOT);
	}

	private String hashRecoveryId(String recoveryId) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(recoveryId.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private ResponseStatusException invalidRecoveryId() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, "The recovery ID is invalid or expired");
	}
}
