package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminPasswordRecoveryServiceTests {

	@Mock
	private AdminCredentialRepository credentialRepository;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	@Test
	void recoveryIdResetsConfiguredAdminPasswordAndCanOnlyBeUsedOnce() {
		AdminCredential credential = new AdminCredential("admin@example.com", passwordEncoder.encode("old-password"));
		when(credentialRepository.findById("admin@example.com")).thenReturn(Optional.of(credential));
		AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
				credentialRepository, passwordEncoder, "admin@example.com", "initial-password");

		String recoveryId = service.generateRecoveryId(" ADMIN@example.com ");
		assertTrue(recoveryId.matches("[A-HJ-NP-Z2-9]{4}(-[A-HJ-NP-Z2-9]{4}){3}"));

		service.resetPassword("admin@example.com", recoveryId, "new-secure-password");

		assertTrue(passwordEncoder.matches("new-secure-password", credential.getPasswordHash()));
		assertNull(credential.getRecoveryIdHash());
		assertNull(credential.getRecoveryIdExpiresAt());
		verify(credentialRepository, times(2)).save(credential);
		assertFalse(passwordEncoder.matches("old-password", credential.getPasswordHash()));
	}

	@Test
	void activeRecoveryIdCannotBeReplacedByAnotherPublicRequest() {
		AdminCredential credential = new AdminCredential("admin@example.com", "hash");
		credential.setRecoveryId("stored-hash", Instant.now().plusSeconds(3600));
		when(credentialRepository.findById("admin@example.com")).thenReturn(Optional.of(credential));
		AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
				credentialRepository, passwordEncoder, "admin@example.com", "initial-password");

		assertNull(service.generateRecoveryId("admin@example.com"));
	}

	@Test
	void unknownEmailDoesNotGenerateRecoveryId() {
		when(credentialRepository.findById("unknown@example.com")).thenReturn(Optional.empty());
		AdminPasswordRecoveryService service = new AdminPasswordRecoveryService(
				credentialRepository, passwordEncoder, "admin@example.com", "initial-password");

		assertNull(service.generateRecoveryId("unknown@example.com"));
	}
}
