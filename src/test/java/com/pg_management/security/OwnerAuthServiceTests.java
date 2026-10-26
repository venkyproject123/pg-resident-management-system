package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
				ownerAccountRepository, ownerOtpService, passwordEncoder, jdbcTemplate, "admin");
	}

	@Test
	void setupRequiredWhenNoOwnerAccountExists() {
		when(ownerAccountRepository.count()).thenReturn(0L);

		assertTrue(ownerAuthService.setupRequired());
	}

	@Test
	void registrationCreatesAnUnapprovedOwnerWithoutOtp() {
		when(ownerAccountRepository.findByUsername("new-owner")).thenReturn(Optional.empty());
		when(ownerAccountRepository.findByWhatsappNumber("+919876543210")).thenReturn(Optional.empty());
		when(ownerAccountRepository.saveAndFlush(any(OwnerAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ownerAuthService.registerOwner(
				"new-owner", "long-password-here", "Sample PG", "Madhapur, Hyderabad", "9876543210");

		org.mockito.ArgumentCaptor<OwnerAccount> owner = org.mockito.ArgumentCaptor.forClass(OwnerAccount.class);
		verify(ownerAccountRepository).saveAndFlush(owner.capture());
		assertFalse(owner.getValue().isApproved());
		assertTrue(passwordEncoder.matches("long-password-here", owner.getValue().getPasswordHash()));
		assertEquals("+919876543210", owner.getValue().getWhatsappNumber());
		assertEquals("Madhapur, Hyderabad", owner.getValue().getLocation());
		verify(jdbcTemplate, org.mockito.Mockito.never()).update(any(String.class), any(Object.class));
	}

	@Test
	void reservedAdminUsernameCannotBeRegisteredAsOwner() {
		org.springframework.web.server.ResponseStatusException exception =
				org.junit.jupiter.api.Assertions.assertThrows(
						org.springframework.web.server.ResponseStatusException.class,
						() -> ownerAuthService.registerOwner(
								"ADMIN", "long-password-here", "Sample PG", "Area", "9876543210"));

		assertTrue(exception.getReason().contains("reserved"));
	}

	@Test
	void firstApprovedOwnerClaimsExistingUnassignedResidents() {
		OwnerAccount owner = new OwnerAccount("owner", "hash", "Sample PG", "+919876543210", false);
		when(ownerAccountRepository.findById(7L)).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.countByApprovedTrue()).thenReturn(0L);

		ownerAuthService.approveOwner(7L);

		assertTrue(owner.isApproved());
		verify(jdbcTemplate).update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", owner.getId());
	}

	@Test
	void approvedOwnerListIncludesApprovedAccounts() {
		OwnerAccount owner = new OwnerAccount(
				"owner", "hash", "Sample PG", "Madhapur, Hyderabad", "+919876543210", true);
		when(ownerAccountRepository.findAllByApprovedTrueOrderByIdAsc()).thenReturn(java.util.List.of(owner));

		var approvedOwners = ownerAuthService.findApprovedOwners();

		assertEquals(1, approvedOwners.size());
		assertEquals("Sample PG", approvedOwners.get(0).pgName());
		assertEquals("owner", approvedOwners.get(0).username());
		assertEquals("+919876543210", approvedOwners.get(0).mobileNumber());
		assertEquals("Madhapur, Hyderabad", approvedOwners.get(0).location());
	}

	@Test
	void pendingOwnerListIncludesLocation() {
		OwnerAccount owner = new OwnerAccount(
				"owner", "hash", "Sample PG", "Madhapur, Hyderabad", "+919876543210", false);
		when(ownerAccountRepository.findAllByApprovedFalseOrderByIdAsc()).thenReturn(java.util.List.of(owner));

		var pendingOwners = ownerAuthService.findPendingOwners();

		assertEquals(1, pendingOwners.size());
		assertEquals("Sample PG", pendingOwners.get(0).pgName());
		assertEquals("owner", pendingOwners.get(0).username());
		assertEquals("+919876543210", pendingOwners.get(0).mobileNumber());
		assertEquals("Madhapur, Hyderabad", pendingOwners.get(0).location());
	}

	@Test
	void updateProfileChangesUsernameAfterMobileVerification() {
		OwnerAccount owner = new OwnerAccount("old-name", "hash", "Sample PG", "+919876543210");
		when(ownerAccountRepository.findByUsername("old-name")).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.findByUsername("new-name")).thenReturn(Optional.empty());
		var request = new OwnerAuthService.ProfileUpdateRequest("new-name", "Updated PG", "9876543211", "123456");

		ownerAuthService.updateProfile("old-name", request);

		assertEquals("new-name", owner.getUsername());
		org.mockito.Mockito.verify(ownerOtpService).updateOwnerProfile(
				owner.getId(), "Updated PG", "9876543211", "123456");
		verify(ownerAccountRepository).save(owner);
	}

	@Test
	void updateProfileRejectsAdminUsername() {
		OwnerAccount owner = new OwnerAccount("owner", "hash", "Sample PG", "+919876543210");
		when(ownerAccountRepository.findByUsername("owner")).thenReturn(Optional.of(owner));
		var request = new OwnerAuthService.ProfileUpdateRequest("ADMIN", "Sample PG", "9876543210", "123456");

		var exception = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> ownerAuthService.updateProfile("owner", request));

		assertTrue(exception.getReason().contains("reserved"));
		org.mockito.Mockito.verifyNoInteractions(ownerOtpService);
	}

}
