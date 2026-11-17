package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OwnerUserDetailsServiceTests {

	@Mock
	private OwnerAccountRepository ownerAccountRepository;

	@Mock
	private AdminCredentialRepository adminCredentialRepository;

	@Test
	void pendingOwnerCannotAuthenticateUntilApproved() {
		OwnerAccount pending = new OwnerAccount("pending-owner", "encoded-password", "Sample PG",
				"+919876543210", false);
		when(ownerAccountRepository.findByUsername("pending-owner")).thenReturn(Optional.of(pending));
		OwnerUserDetailsService service = new OwnerUserDetailsService(ownerAccountRepository, adminCredentialRepository);

		UserDetails owner = service.loadUserByUsername("pending-owner");

		assertFalse(owner.isEnabled());
		assertTrue(owner.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
		assertSame(pending, ((OwnerPrincipal) owner).getOwner());
	}

	@Test
	void removedOwnerCannotAuthenticate() {
		OwnerAccount removed = new OwnerAccount("removed-owner", "encoded-password", "Sample PG",
				"+919876543210", true);
		removed.remove();
		when(ownerAccountRepository.findByUsername("removed-owner")).thenReturn(Optional.of(removed));
		OwnerUserDetailsService service = new OwnerUserDetailsService(ownerAccountRepository, adminCredentialRepository);

		UserDetails owner = service.loadUserByUsername("removed-owner");

		assertFalse(owner.isEnabled());
	}

	@Test
	void configuredAdminHasOnlyAdminRole() {
		AdminCredential credential = new AdminCredential("admin@example.com",
				new BCryptPasswordEncoder(4).encode("admin-password"));
		when(adminCredentialRepository.findById("admin@example.com")).thenReturn(Optional.of(credential));
		OwnerUserDetailsService service = new OwnerUserDetailsService(
				ownerAccountRepository, adminCredentialRepository);

		UserDetails admin = service.loadUserByUsername("ADMIN@example.com");

		assertTrue(admin.isEnabled());
		assertTrue(admin.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
		assertTrue(admin.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
	}
}
