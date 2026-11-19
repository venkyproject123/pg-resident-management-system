package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
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

import com.pg_management.resident.storage.PrivateDocumentStorageService;

@ExtendWith(MockitoExtension.class)
class OwnerAuthServiceTests {

	@Mock
	private OwnerAccountRepository ownerAccountRepository;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Mock
	private PrivateDocumentStorageService documentStorage;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
	private OwnerAuthService ownerAuthService;

	@BeforeEach
	void createService() {
		ownerAuthService = new OwnerAuthService(
				ownerAccountRepository, passwordEncoder, jdbcTemplate, documentStorage, "admin");
	}

	@Test
	void setupRequiredWhenNoOwnerAccountExists() {
		when(ownerAccountRepository.count()).thenReturn(0L);

		assertTrue(ownerAuthService.setupRequired());
	}

	@Test
	void registrationCreatesAnUnapprovedOwnerWithoutOtp() {
		when(ownerAccountRepository.findByUsername("owner@example.com")).thenReturn(Optional.empty());
		when(ownerAccountRepository.findByMobileNumber("+919876543210")).thenReturn(Optional.empty());
		when(ownerAccountRepository.saveAndFlush(any(OwnerAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ownerAuthService.registerOwner(
				"Owner Name",
				"9876543210",
				"OWNER@example.com",
				"Sample PG",
				"boys",
				"Madhapur",
				"Hyderabad",
				"long-password-here");

		org.mockito.ArgumentCaptor<OwnerAccount> owner = org.mockito.ArgumentCaptor.forClass(OwnerAccount.class);
		verify(ownerAccountRepository).saveAndFlush(owner.capture());
		assertFalse(owner.getValue().isApproved());
		assertTrue(passwordEncoder.matches("long-password-here", owner.getValue().getPasswordHash()));
		assertEquals("owner@example.com", owner.getValue().getUsername());
		assertEquals("+919876543210", owner.getValue().getMobileNumber());
		assertEquals("Owner Name", owner.getValue().getOwnerName());
		assertEquals("Sample PG", owner.getValue().getPgName());
		assertEquals("BOYS", owner.getValue().getPropertyType());
		assertEquals("Madhapur", owner.getValue().getLocality());
		assertEquals("Hyderabad", owner.getValue().getCity());
		assertEquals("Madhapur, Hyderabad", owner.getValue().getLocation());
		verify(jdbcTemplate, org.mockito.Mockito.never()).update(any(String.class), any(Object.class));
	}

	@Test
	void reservedAdminUsernameCannotBeRegisteredAsOwner() {
		org.springframework.web.server.ResponseStatusException exception =
				org.junit.jupiter.api.Assertions.assertThrows(
						org.springframework.web.server.ResponseStatusException.class,
						() -> ownerAuthService.registerOwner(
								"Owner Name",
								"9876543210",
								"ADMIN",
								"Sample PG",
								"BOYS",
								"Area",
								"City",
								"long-password-here"));

		assertTrue(exception.getReason().contains("reserved"));
	}

	@Test
	void registrationRejectsAnAlreadyRegisteredMobileNumber() {
		OwnerAccount existingOwner = new OwnerAccount("another@example.com", "hash", "Another PG", "+919876543210", true);
		when(ownerAccountRepository.findByUsername("owner@example.com")).thenReturn(Optional.empty());
		when(ownerAccountRepository.findByMobileNumber("+919876543210")).thenReturn(Optional.of(existingOwner));

		var exception = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> ownerAuthService.registerOwner(
				"Owner Name",
				"9876543210",
				"owner@example.com",
				"Sample PG",
				"BOYS",
				"Madhapur",
				"Hyderabad",
				"long-password-here"));

		assertTrue(exception.getReason().contains("mobile number is already registered"));
	}

	@Test
	void registrationPurgesPreviouslyRemovedOwnerAndMakesEmailAndMobileAvailable() {
		OwnerAccount removedOwner = new OwnerAccount("owner@example.com", "hash", "Old PG", "+919876543210", true);
		removedOwner.remove();
		when(ownerAccountRepository.findByUsername("owner@example.com")).thenReturn(Optional.of(removedOwner));
		when(ownerAccountRepository.findByMobileNumber("+919876543210")).thenReturn(Optional.empty());
		when(ownerAccountRepository.saveAndFlush(any(OwnerAccount.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ownerAuthService.registerOwner(
				"Owner Name",
				"9876543210",
				"owner@example.com",
				"New PG",
				"BOYS",
				"Madhapur",
				"Hyderabad",
				"long-password-here");

		verify(jdbcTemplate).update(contains("DELETE payment"), eq(removedOwner.getId()));
		verify(jdbcTemplate).update(contains("DELETE document"), eq(removedOwner.getId()));
		verify(jdbcTemplate).update("DELETE FROM residents WHERE owner_id = ?", removedOwner.getId());
		verify(jdbcTemplate).update("DELETE FROM owner_rooms WHERE owner_id = ?", removedOwner.getId());
		verify(ownerAccountRepository).delete(removedOwner);
		verify(ownerAccountRepository).flush();
		verify(ownerAccountRepository).saveAndFlush(any(OwnerAccount.class));
	}

	@Test
	void ownerCanUpdateTheirProfileAndMobileNumber() {
		OwnerAccount owner = new OwnerAccount("owner@example.com", "hash", "Old PG", "+919876543210", true);
		when(ownerAccountRepository.findByUsername("owner@example.com")).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.existsByMobileNumberAndIdNot("+919123456789", owner.getId())).thenReturn(false);
		when(ownerAccountRepository.save(owner)).thenReturn(owner);

		OwnerAuthService.OwnerProfile profile = ownerAuthService.updateProfile(
				"owner@example.com",
				"New Username",
				"9123456789",
				"New PG",
				"GIRLS",
				"Banjara Hills",
				"Hyderabad");

		assertEquals("New Username", profile.ownerName());
		assertEquals("New PG", profile.pgName());
		assertEquals("+919123456789", profile.mobileNumber());
		assertEquals("GIRLS", profile.propertyType());
		assertEquals("Banjara Hills", profile.locality());
		assertEquals("Hyderabad", profile.city());
		verify(ownerAccountRepository).save(owner);
	}

	@Test
	void ownerCannotChangeProfileToAnotherOwnersMobileNumber() {
		OwnerAccount owner = new OwnerAccount("owner@example.com", "hash", "Old PG", "+919876543210", true);
		when(ownerAccountRepository.findByUsername("owner@example.com")).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.existsByMobileNumberAndIdNot("+919123456789", owner.getId())).thenReturn(true);

		var exception = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> ownerAuthService.updateProfile(
						"owner@example.com",
						"Owner Name",
						"9123456789",
						"Sample PG",
						"BOYS",
						"Area",
						"City"));

		assertTrue(exception.getReason().contains("already used"));
	}

	@Test
	void firstApprovedOwnerClaimsExistingUnassignedResidents() {
		OwnerAccount owner = new OwnerAccount("owner", "hash", "Sample PG", "+919876543210", false);
		when(ownerAccountRepository.findById(7L)).thenReturn(Optional.of(owner));
		when(ownerAccountRepository.countByApprovedTrueAndRemovedFalse()).thenReturn(0L);

		ownerAuthService.approveOwner(7L);

		assertTrue(owner.isApproved());
		verify(jdbcTemplate).update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", owner.getId());
	}

	@Test
	void approvedOwnerListIncludesApprovedAccounts() {
		OwnerAccount owner = new OwnerAccount(
				"owner", "hash", "Sample PG", "Madhapur, Hyderabad", "+919876543210", true);
		when(ownerAccountRepository.findAllByApprovedTrueAndRemovedFalseOrderByIdAsc()).thenReturn(java.util.List.of(owner));

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
		when(ownerAccountRepository.findAllByApprovedFalseAndRemovedFalseOrderByIdAsc()).thenReturn(java.util.List.of(owner));

		var pendingOwners = ownerAuthService.findPendingOwners();

		assertEquals(1, pendingOwners.size());
		assertEquals("Sample PG", pendingOwners.get(0).pgName());
		assertEquals("owner", pendingOwners.get(0).username());
		assertEquals("+919876543210", pendingOwners.get(0).mobileNumber());
		assertEquals("Madhapur, Hyderabad", pendingOwners.get(0).location());
	}

	@Test
	void removingOwnerPermanentlyDeletesAccountAndOwnedData() {
		OwnerAccount owner = new OwnerAccount("owner", "hash", "Sample PG", "+919876543210", true);
		when(ownerAccountRepository.findById(7L)).thenReturn(Optional.of(owner));

		ownerAuthService.removeOwner(7L);

		assertFalse(owner.isRemoved());
		assertTrue(owner.isApproved());
		verify(jdbcTemplate).update(contains("DELETE payment"), eq(owner.getId()));
		verify(jdbcTemplate).update(contains("DELETE document"), eq(owner.getId()));
		verify(jdbcTemplate).update("DELETE FROM residents WHERE owner_id = ?", owner.getId());
		verify(jdbcTemplate).update("DELETE FROM owner_rooms WHERE owner_id = ?", owner.getId());
		verify(ownerAccountRepository).delete(owner);
		verify(ownerAccountRepository).flush();
	}

}
