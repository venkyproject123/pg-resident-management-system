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

	@Column(name = "pg_name", nullable = false, length = 100)
	private String pgName;

	@Column(name = "location", length = 150)
	private String location;

	@Column(name = "whatsapp_number", unique = true, length = 20)
	private String whatsappNumber;

	@Column(name = "whatsapp_verified", nullable = false)
	private boolean whatsappVerified;

	@Column(nullable = false, columnDefinition = "boolean not null default true")
	private boolean approved = true;

	@Column(name = "rent_due_day", nullable = false, columnDefinition = "int not null default 5")
	private int legacyRentDueDay = 5;

	protected OwnerAccount() {
	}

	public OwnerAccount(String username, String passwordHash, String pgName, String whatsappNumber) {
		this(username, passwordHash, pgName, null, whatsappNumber, true);
	}

	public OwnerAccount(String username, String passwordHash, String pgName, String whatsappNumber, boolean approved) {
		this(username, passwordHash, pgName, null, whatsappNumber, approved);
	}

	public OwnerAccount(
			String username, String passwordHash, String pgName, String location, String whatsappNumber, boolean approved) {
		this.accountScope = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		this.username = username;
		this.passwordHash = passwordHash;
		this.pgName = pgName;
		this.location = location;
		this.whatsappNumber = whatsappNumber;
		this.approved = approved;
	}

	public OwnerAccount(String username, String passwordHash) {
		this(username, passwordHash, "Test PG", null);
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

	public String getPgName() {
		return pgName;
	}

	public String getLocation() {
		return location;
	}

	public String getWhatsappNumber() {
		return whatsappNumber;
	}

	public boolean isApproved() {
		return approved;
	}

	public void approve() {
		this.approved = true;
	}

	public void updateProfile(String pgName, String whatsappNumber) {
		this.pgName = pgName;
		this.whatsappNumber = whatsappNumber;
		this.whatsappVerified = true;
	}

	public void updateUsername(String username) {
		this.username = username;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
