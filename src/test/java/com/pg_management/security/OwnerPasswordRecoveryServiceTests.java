package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OwnerPasswordRecoveryServiceTests {

	@Mock
	private OwnerAccountRepository ownerAccountRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private OwnerPasswordRecoveryService service;

	@BeforeEach
	void createService() {
		service = new OwnerPasswordRecoveryService(ownerAccountRepository, passwordEncoder);
	}

	@Test
	void administratorCanGenerateRecoveryIdAndItIsStoredAsAHash() {
		OwnerAccount owner = new OwnerAccount("owner@example.com", "old-hash", "Sample PG", "9876543210");
		when(ownerAccountRepository.findById(7L)).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.existsByRecoveryIdHash(any())).thenReturn(false);
		when(ownerAccountRepository.save(any(OwnerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

		String recoveryId = service.generateRecoveryId(7L);

		assertTrue(recoveryId.matches("[A-HJ-NP-Z2-9]{16}"));
		assertNotEquals(recoveryId, owner.getRecoveryIdHash());
		assertEquals(hash(recoveryId), owner.getRecoveryIdHash());
		assertTrue(owner.getRecoveryIdExpiresAt().isAfter(java.time.Instant.now()));
		verify(ownerAccountRepository).save(owner);
	}

	@Test
	void recoveryIdResetsPasswordOnceAndIsCleared() {
		String recoveryId = "ABCDEFGHJKLMNPQR";
		OwnerAccount owner = new OwnerAccount("owner@example.com", "old-hash", "Sample PG", "9876543210");
		owner.setRecoveryId(hash(recoveryId), java.time.Instant.now().plusSeconds(3600));
		when(ownerAccountRepository.findByRecoveryIdHashForUpdate(hash(recoveryId))).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.save(any(OwnerAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.resetPassword(recoveryId, "a-new-long-owner-password");

		assertTrue(passwordEncoder.matches("a-new-long-owner-password", owner.getPasswordHash()));
		assertEquals(null, owner.getRecoveryIdHash());
		assertEquals(null, owner.getRecoveryIdExpiresAt());
		verify(ownerAccountRepository).save(owner);
	}

	@Test
	void invalidRecoveryIdDoesNotChangeThePassword() {
		when(ownerAccountRepository.findByRecoveryIdHashForUpdate(hash("INVALIDRECOVERYID")))
				.thenReturn(Optional.empty());

		assertThrows(ResponseStatusException.class,
				() -> service.resetPassword("INVALIDRECOVERYID", "a-new-long-owner-password"));

		verify(ownerAccountRepository, never()).save(any(OwnerAccount.class));
	}

	private String hash(String recoveryId) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(recoveryId.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new AssertionError(exception);
		}
	}
}
