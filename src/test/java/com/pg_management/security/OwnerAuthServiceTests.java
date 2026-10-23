package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OwnerAuthServiceTests {

	@Mock
	private OwnerAccountRepository ownerAccountRepository;

	@Mock
	private OwnerOtpService ownerOtpService;

	@Mock
	private JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private OwnerAuthService ownerAuthService;

	@BeforeEach
	void createService() {
		ownerAuthService = new OwnerAuthService(
				ownerAccountRepository, ownerOtpService, passwordEncoder, jdbcTemplate);
	}

	@Test
	void setupRequiredWhenNoOwnerAccountExists() {
		when(ownerAccountRepository.count()).thenReturn(0L);

		assertTrue(ownerAuthService.setupRequired());
	}

	@Test
	void firstRegistrationClaimsExistingUnassignedResidents() {
		OwnerAccount owner = new OwnerAccount("owner", passwordEncoder.encode("long-password-here"),
				"Sample PG", "+919876543210");
		when(ownerAccountRepository.count()).thenReturn(0L);
		when(ownerOtpService.registerOwner("owner", "long-password-here", "Sample PG", "+919876543210", "123456"))
				.thenReturn(owner);

		ownerAuthService.registerOwner("owner", "long-password-here", "Sample PG", "+919876543210", "123456");

		verify(jdbcTemplate).update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", owner.getId());
	}

	@Test
	void laterOwnerRegistrationDoesNotReassignExistingResidents() {
		OwnerAccount owner = new OwnerAccount("second-owner", "hash", "Second PG", "+919876543211");
		when(ownerAccountRepository.count()).thenReturn(1L);
		when(ownerOtpService.registerOwner("second-owner", "another-long-password", "Second PG",
				"+919876543211", "654321")).thenReturn(owner);

		ownerAuthService.registerOwner(
				"second-owner", "another-long-password", "Second PG", "+919876543211", "654321");

		verify(jdbcTemplate, org.mockito.Mockito.never())
				.update(any(String.class), any(Object.class));
	}

	@Test
	void passwordChangeVerifiesCurrentPassword() {
		OwnerAccount owner = new OwnerAccount("owner", passwordEncoder.encode("old-password-here"));
		when(ownerAccountRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

		ownerAuthService.changePassword("owner", "old-password-here", "new-password-here");

		assertTrue(passwordEncoder.matches("new-password-here", owner.getPasswordHash()));
		verify(ownerAccountRepository).save(owner);
	}
}
