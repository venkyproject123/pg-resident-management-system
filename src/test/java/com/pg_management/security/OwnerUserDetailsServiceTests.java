package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

	@Test
	void pendingOwnerCannotAuthenticateUntilApproved() {
		OwnerAccount pending = new OwnerAccount("pending-owner", "encoded-password", "Sample PG",
				"+919876543210", false);
		when(ownerAccountRepository.findByUsername("pending-owner")).thenReturn(Optional.of(pending));
		OwnerUserDetailsService service = new OwnerUserDetailsService(
				ownerAccountRepository, new BCryptPasswordEncoder(4), "admin", "admin-password");

		UserDetails owner = service.loadUserByUsername("pending-owner");

		assertFalse(owner.isEnabled());
		assertTrue(owner.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
	}

	@Test
	void environmentAdminHasOnlyAdminRole() {
		OwnerUserDetailsService service = new OwnerUserDetailsService(
				ownerAccountRepository, new BCryptPasswordEncoder(4), "admin", "admin-password");

		UserDetails admin = service.loadUserByUsername("ADMIN");

		assertTrue(admin.isEnabled());
		assertTrue(admin.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
		assertTrue(admin.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("ROLE_OWNER")));
	}
}
