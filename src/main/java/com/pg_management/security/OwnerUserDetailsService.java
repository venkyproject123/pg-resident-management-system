package com.pg_management.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OwnerUserDetailsService implements UserDetailsService {

	private static final Logger LOGGER = LoggerFactory.getLogger(OwnerUserDetailsService.class);

	private final OwnerAccountRepository ownerAccountRepository;
	private final String adminUsername;
	private final String adminPasswordHash;

	public OwnerUserDetailsService(
			OwnerAccountRepository ownerAccountRepository,
			PasswordEncoder passwordEncoder,
			@Value("${app.admin.username:}") String adminUsername,
			@Value("${app.admin.password:}") String adminPassword) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.adminUsername = adminUsername;
		this.adminPasswordHash = adminPassword.isBlank() ? "" : passwordEncoder.encode(adminPassword);
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		long startedAt = System.nanoTime();
		try {
			if (!adminUsername.isBlank() && username.equalsIgnoreCase(adminUsername)) {
				if (adminPasswordHash.isBlank()) {
					throw new UsernameNotFoundException("Admin account is not configured");
				}
				return User.withUsername(adminUsername)
						.password(adminPasswordHash)
						.roles("ADMIN")
						.build();
			}
			OwnerAccount owner = ownerAccountRepository.findByUsername(username)
					.orElseThrow(() -> new UsernameNotFoundException("Owner account not found"));
			return new OwnerPrincipal(owner);
		} finally {
			long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
			LOGGER.info("user_details_lookup type={} durationMs={}",
					!adminUsername.isBlank() && username.equalsIgnoreCase(adminUsername) ? "admin" : "owner",
					durationMs);
		}
	}
}
