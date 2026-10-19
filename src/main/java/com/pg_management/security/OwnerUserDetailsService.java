package com.pg_management.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class OwnerUserDetailsService implements UserDetailsService {

	private final OwnerAccountRepository ownerAccountRepository;

	public OwnerUserDetailsService(OwnerAccountRepository ownerAccountRepository) {
		this.ownerAccountRepository = ownerAccountRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new UsernameNotFoundException("Owner account not found"));
		return User.withUsername(owner.getUsername())
				.password(owner.getPasswordHash())
				.roles("OWNER")
				.build();
	}
}
