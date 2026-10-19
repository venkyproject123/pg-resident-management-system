package com.pg_management.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "owner_accounts")
public class OwnerAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_scope", nullable = false, unique = true, length = 16)
	private String accountScope = "OWNER";

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	protected OwnerAccount() {
	}

	public OwnerAccount(String username, String passwordHash) {
		this.accountScope = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		this.username = username;
		this.passwordHash = passwordHash;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
