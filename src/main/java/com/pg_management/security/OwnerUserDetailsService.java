package com.pg_management.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class OwnerUserDetailsService implements UserDetailsService {

	private static final Logger LOGGER = LoggerFactory.getLogger(OwnerUserDetailsService.class);

	private final OwnerAccountRepository ownerAccountRepository;
	private final AdminCredentialRepository adminCredentialRepository;

	public OwnerUserDetailsService(
			OwnerAccountRepository ownerAccountRepository,
			AdminCredentialRepository adminCredentialRepository) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.adminCredentialRepository = adminCredentialRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		long startedAt = System.nanoTime();
		String accountType = "owner";
		try {
			var adminCredential = adminCredentialRepository.findById(username.trim().toLowerCase(java.util.Locale.ROOT));
			if (adminCredential.isPresent()) {
				accountType = "admin";
				return User.withUsername(adminCredential.get().getEmail())
						.password(adminCredential.get().getPasswordHash())
						.roles("ADMIN")
						.build();
			}
			OwnerAccount owner = ownerAccountRepository.findByUsername(username)
					.orElseThrow(() -> new UsernameNotFoundException("Owner account not found"));
			return new OwnerPrincipal(owner);
		} finally {
			long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
			LOGGER.info("user_details_lookup type={} durationMs={}", accountType, durationMs);
		}
	}
}
