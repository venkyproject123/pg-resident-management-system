package com.pg_management.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class OwnerPrincipal implements UserDetails {

	private final OwnerAccount owner;

	public OwnerPrincipal(OwnerAccount owner) {
		this.owner = owner;
	}

	public OwnerAccount getOwner() {
		return owner;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_OWNER"));
	}

	@Override
	public String getPassword() {
		return owner.getPasswordHash();
	}

	@Override
	public String getUsername() {
		return owner.getUsername();
	}

	@Override
	public boolean isEnabled() {
		return owner.isApproved();
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}
}
