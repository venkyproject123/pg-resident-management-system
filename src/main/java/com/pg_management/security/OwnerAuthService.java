package com.pg_management.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(noRollbackFor = ResponseStatusException.class)
public class OwnerAuthService {

	private static final Logger LOGGER = LoggerFactory.getLogger(OwnerAuthService.class);

	private final OwnerAccountRepository ownerAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;
	private final String adminEmail;

	public OwnerAuthService(
			OwnerAccountRepository ownerAccountRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate,
			@Value("${app.admin.email:}") String adminEmail) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
		this.adminEmail = adminEmail.trim().toLowerCase(java.util.Locale.ROOT);
	}

	@Transactional(readOnly = true)
	public boolean setupRequired() {
		return ownerAccountRepository.count() == 0;
	}

	public void registerOwner(
			String ownerName,
			String mobileNumber,
			String email,
			String pgName,
			String propertyType,
			String locality,
			String city,
			String password) {
		long startedAt = System.nanoTime();
		long emailLookupMs = 0;
		long mobileLookupMs = 0;
		long passwordHashMs = 0;
		long databaseSaveMs = 0;
		try {
			String normalizedEmail = email.trim().toLowerCase();
			if (!adminEmail.isBlank() && normalizedEmail.equalsIgnoreCase(adminEmail)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is reserved");
			}
			long stageStartedAt = System.nanoTime();
			OwnerAccount emailOwner = ownerAccountRepository.findByUsername(normalizedEmail).orElse(null);
			emailLookupMs = elapsedMs(stageStartedAt);
			if (emailOwner != null && emailOwner.isRemoved()) {
				deleteOwnerData(emailOwner);
			} else if (emailOwner != null) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "This email address is already registered. Sign in or use another email.");
			}
			String normalizedMobileNumber = normalizeMobileNumber(mobileNumber);
			stageStartedAt = System.nanoTime();
			OwnerAccount mobileOwner = ownerAccountRepository.findByMobileNumber(normalizedMobileNumber).orElse(null);
			mobileLookupMs = elapsedMs(stageStartedAt);
			if (mobileOwner != null && mobileOwner.isRemoved()) {
				deleteOwnerData(mobileOwner);
			} else if (mobileOwner != null) {
				throw new ResponseStatusException(
						HttpStatus.CONFLICT, "This mobile number is already registered. Use another number.");
			}
			stageStartedAt = System.nanoTime();
			String passwordHash = passwordEncoder.encode(password);
			passwordHashMs = elapsedMs(stageStartedAt);
			stageStartedAt = System.nanoTime();
			String normalizedLocality = locality.trim();
			String normalizedCity = city.trim();
			String location = normalizedLocality + ", " + normalizedCity;
			ownerAccountRepository.saveAndFlush(new OwnerAccount(
					normalizedEmail,
					passwordHash,
					pgName.trim(),
					ownerName.trim(),
					propertyType.trim().toUpperCase(),
					normalizedLocality,
					normalizedCity,
					location,
					normalizedMobileNumber,
					false));
			databaseSaveMs = elapsedMs(stageStartedAt);
		} finally {
			LOGGER.info(
					"owner_registration emailLookupMs={} mobileLookupMs={} passwordHashMs={} databaseSaveMs={} serviceDurationMs={}",
					emailLookupMs, mobileLookupMs, passwordHashMs, databaseSaveMs, elapsedMs(startedAt));
		}
	}

	@Transactional(readOnly = true)
	public List<OwnerSummary> findPendingOwners() {
		return ownerAccountRepository.findAllByApprovedFalseAndRemovedFalseOrderByIdAsc().stream()
				.map(owner -> new OwnerSummary(
						owner.getId(), owner.getUsername(), owner.getPgName(), owner.getMobileNumber(),
						owner.getLocation(), owner.getOwnerName(), owner.getPropertyType(),
						owner.getLocality(), owner.getCity()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<OwnerSummary> findApprovedOwners() {
		return ownerAccountRepository.findAllByApprovedTrueAndRemovedFalseOrderByIdAsc().stream()
				.map(owner -> new OwnerSummary(
						owner.getId(), owner.getUsername(), owner.getPgName(), owner.getMobileNumber(),
						owner.getLocation(), owner.getOwnerName(), owner.getPropertyType(),
						owner.getLocality(), owner.getCity()))
				.toList();
	}

	@Transactional(readOnly = true)
	public OwnerOverview findOwnersOverview() {
		return new OwnerOverview(findPendingOwners(), findApprovedOwners());
	}

	public void approveOwner(Long ownerId) {
		OwnerAccount owner = ownerAccountRepository.findById(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));
		if (owner.isRemoved()) {
			throw new ResponseStatusException(HttpStatus.GONE, "This owner registration has been removed");
		}
		if (owner.isApproved()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This owner is already approved");
		}
		boolean firstApprovedOwner = ownerAccountRepository.countByApprovedTrueAndRemovedFalse() == 0;
		owner.approve();
		ownerAccountRepository.save(owner);
		if (firstApprovedOwner) {
			jdbcTemplate.update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", owner.getId());
		}
	}

	public void removeOwner(Long ownerId) {
		OwnerAccount owner = ownerAccountRepository.findById(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
		deleteOwnerData(owner);
	}

	private void deleteOwnerData(OwnerAccount owner) {
		Long ownerId = owner.getId();
		jdbcTemplate.update("""
				DELETE payment
				FROM rent_payments payment
				JOIN residents resident ON resident.id = payment.resident_id
				WHERE resident.owner_id = ?
				""", ownerId);
		jdbcTemplate.update("""
				DELETE document
				FROM resident_document_files document
				JOIN residents resident
				  ON document.storage_key = resident.photo_storage_key
				  OR document.storage_key = resident.identity_proof_storage_key
				WHERE resident.owner_id = ?
				""", ownerId);
		jdbcTemplate.update("DELETE FROM residents WHERE owner_id = ?", ownerId);
		jdbcTemplate.update("DELETE FROM owner_rooms WHERE owner_id = ?", ownerId);
		ownerAccountRepository.delete(owner);
		ownerAccountRepository.flush();
	}

	@Transactional(readOnly = true)
	public OwnerProfile getProfile(String username) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		return new OwnerProfile(
				owner.getUsername(),
				owner.getPgName(),
				owner.getLocation(),
				owner.getMobileNumber(),
				owner.getOwnerName(),
				owner.getPropertyType(),
				owner.getLocality(),
				owner.getCity());
	}

	public OwnerProfile updateProfile(
			String username,
			String ownerName,
			String mobileNumber,
			String pgName,
			String propertyType,
			String locality,
			String city) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.filter(account -> !account.isRemoved())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		String normalizedMobileNumber = normalizeMobileNumber(mobileNumber);
		if (ownerAccountRepository.existsByMobileNumberAndIdNot(normalizedMobileNumber, owner.getId())) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, "This mobile number is already used by another owner.");
		}
		String normalizedLocality = locality.trim();
		String normalizedCity = city.trim();
		owner.setRegistrationDetails(
				ownerName.trim(),
				propertyType.trim().toUpperCase(),
				normalizedLocality,
				normalizedCity,
				normalizedLocality + ", " + normalizedCity,
				normalizedMobileNumber);
		owner.setPgName(pgName.trim());
		ownerAccountRepository.save(owner);
		return getProfile(username);
	}

	public record OwnerProfile(
			String username,
			String pgName,
			String location,
			String mobileNumber,
			String ownerName,
			String propertyType,
			String locality,
			String city) {
	}

	public record OwnerSummary(
			Long id,
			String username,
			String pgName,
			String mobileNumber,
			String location,
			String ownerName,
			String propertyType,
			String locality,
			String city) {
	}

	public record OwnerOverview(List<OwnerSummary> pending, List<OwnerSummary> approved) {
	}

	private String normalizeMobileNumber(String mobileNumber) {
		if (mobileNumber == null || !mobileNumber.matches("[6-9][0-9]{9}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number");
		}
		return "+91" + mobileNumber;
	}

	private long elapsedMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
