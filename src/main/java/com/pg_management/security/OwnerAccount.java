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

	@Column(name = "mobile_number", length = 20)
	private String mobileNumber;

	@Column(nullable = false, columnDefinition = "boolean not null default true")
	private boolean approved = true;

	@Column(nullable = false, columnDefinition = "boolean not null default false")
	private boolean removed;

	@Column(name = "rent_due_day", nullable = false, columnDefinition = "int not null default 5")
	private int legacyRentDueDay = 5;

	protected OwnerAccount() {
	}

	public OwnerAccount(String username, String passwordHash, String pgName, String mobileNumber) {
		this(username, passwordHash, pgName, null, mobileNumber, true);
	}

	public OwnerAccount(String username, String passwordHash, String pgName, String mobileNumber, boolean approved) {
		this(username, passwordHash, pgName, null, mobileNumber, approved);
	}

	public OwnerAccount(
			String username, String passwordHash, String pgName, String location, String mobileNumber, boolean approved) {
		this.accountScope = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
		this.username = username;
		this.passwordHash = passwordHash;
		this.pgName = pgName;
		this.location = location;
		this.mobileNumber = mobileNumber;
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

	public String getMobileNumber() {
		return mobileNumber;
	}

	public boolean isApproved() {
		return approved;
	}

	public boolean isRemoved() {
		return removed;
	}

	public void approve() {
		this.approved = true;
	}

	public void remove() {
		this.approved = false;
		this.removed = true;
	}

	public void updatePgName(String pgName) {
		this.pgName = pgName;
	}

	public void updateUsername(String username) {
		this.username = username;
	}

	public void updateLocation(String location) {
		this.location = location;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
