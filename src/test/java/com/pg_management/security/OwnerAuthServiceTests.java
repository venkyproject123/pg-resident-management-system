package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OwnerAuthServiceTests {

	@Mock
	private OwnerAccountRepository ownerAccountRepository;
	@Mock
	private JdbcTemplate jdbcTemplate;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

	private OwnerAuthService ownerAuthService;

	@BeforeEach
	void createService() {
		ownerAuthService = new OwnerAuthService(ownerAccountRepository, passwordEncoder, jdbcTemplate, "setup-key", "registration-key");
	}

	@Test
	void setupRequiredWhenNoOwnerAccountExists() {
		when(ownerAccountRepository.count()).thenReturn(0L);

		assertTrue(ownerAuthService.setupRequired());
	}

	@Test
	void setupCreatesOwnerUsingSetupToken() {
		when(ownerAccountRepository.findByUsername("owner")).thenReturn(Optional.empty());
		when(ownerAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ownerAuthService.setupOwner("setup-key", "owner", "long-password-here");

		verify(ownerAccountRepository).saveAndFlush(argThat(owner ->
				owner.getUsername().equals("owner")
						&& owner.getPasswordHash().startsWith("$2a$")
						&& owner.getPasswordHash().length() > 50));
		verify(jdbcTemplate).update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", (Object) null);
	}

	@Test
	void allowsAnotherOwnerToRegisterWithTheRegistrationToken() {
		when(ownerAccountRepository.count()).thenReturn(1L);
		when(ownerAccountRepository.findByUsername("second-owner")).thenReturn(Optional.empty());
		when(ownerAccountRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

		ownerAuthService.setupOwner("registration-key", "second-owner", "another-long-password");

		verify(ownerAccountRepository).saveAndFlush(argThat(owner -> owner.getUsername().equals("second-owner")));
	}

	@Test
	void registrationTokenCannotBeUsedToResetAnotherOwnersPassword() {
		assertThrows(ResponseStatusException.class,
				() -> ownerAuthService.resetPassword("registration-key", "owner", "new-password-here"));
	}

	@Test
	void resetPasswordUpdatesPasswordWithRecoveryToken() {
		OwnerAccount owner = new OwnerAccount("owner", passwordEncoder.encode("old-password-here"));
		when(ownerAccountRepository.findByUsername("owner")).thenReturn(Optional.of(owner));

		ownerAuthService.resetPassword("setup-key", "owner", "new-password-here");

		assertTrue(passwordEncoder.matches("new-password-here", owner.getPasswordHash()));
	}

	@Test
	void invalidRecoveryTokenCannotResetPassword() {
		assertThrows(ResponseStatusException.class,
				() -> ownerAuthService.resetPassword("incorrect-token", "owner", "new-password-here"));
	}
}
