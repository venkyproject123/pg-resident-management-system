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
	private final String adminUsername;

	public OwnerAuthService(
			OwnerAccountRepository ownerAccountRepository,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate,
			@Value("${app.admin.username:}") String adminUsername) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
		this.adminUsername = adminUsername;
	}

	@Transactional(readOnly = true)
	public boolean setupRequired() {
		return ownerAccountRepository.count() == 0;
	}

	public void registerOwner(
			String username,
			String password,
			String pgName,
			String location,
			String mobileNumber) {
		long startedAt = System.nanoTime();
		long usernameLookupMs = 0;
		long mobileLookupMs = 0;
		long passwordHashMs = 0;
		long databaseSaveMs = 0;
		try {
			if (!adminUsername.isBlank() && username.equalsIgnoreCase(adminUsername)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is reserved");
			}
			long stageStartedAt = System.nanoTime();
			boolean usernameExists = ownerAccountRepository.existsByUsername(username);
			usernameLookupMs = elapsedMs(stageStartedAt);
			if (usernameExists) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already registered");
			}
			String normalizedNumber = mobileNumber.matches("[6-9][0-9]{9}") ? "+91" + mobileNumber : mobileNumber;
			stageStartedAt = System.nanoTime();
			boolean mobileNumberExists = ownerAccountRepository.existsByMobileNumber(normalizedNumber);
			mobileLookupMs = elapsedMs(stageStartedAt);
			if (mobileNumberExists) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "That mobile number is already registered");
			}
			stageStartedAt = System.nanoTime();
			String passwordHash = passwordEncoder.encode(password);
			passwordHashMs = elapsedMs(stageStartedAt);
			stageStartedAt = System.nanoTime();
			ownerAccountRepository.saveAndFlush(new OwnerAccount(
					username, passwordHash, pgName.trim(), location.trim(), normalizedNumber, false));
			databaseSaveMs = elapsedMs(stageStartedAt);
		} finally {
			LOGGER.info(
					"owner_registration usernameLookupMs={} mobileLookupMs={} passwordHashMs={} databaseSaveMs={} serviceDurationMs={}",
					usernameLookupMs, mobileLookupMs, passwordHashMs, databaseSaveMs, elapsedMs(startedAt));
		}
	}

	@Transactional(readOnly = true)
	public List<OwnerSummary> findPendingOwners() {
		return ownerAccountRepository.findAllByApprovedFalseOrderByIdAsc().stream()
				.map(owner -> new OwnerSummary(
						owner.getId(), owner.getUsername(), owner.getPgName(), owner.getMobileNumber(),
						owner.getLocation()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<OwnerSummary> findApprovedOwners() {
		return ownerAccountRepository.findAllByApprovedTrueOrderByIdAsc().stream()
				.map(owner -> new OwnerSummary(
						owner.getId(), owner.getUsername(), owner.getPgName(), owner.getMobileNumber(),
						owner.getLocation()))
				.toList();
	}

	@Transactional(readOnly = true)
	public OwnerOverview findOwnersOverview() {
		return new OwnerOverview(findPendingOwners(), findApprovedOwners());
	}

	public void approveOwner(Long ownerId) {
		OwnerAccount owner = ownerAccountRepository.findById(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Registration not found"));
		if (owner.isApproved()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This owner is already approved");
		}
		boolean firstApprovedOwner = ownerAccountRepository.countByApprovedTrue() == 0;
		owner.approve();
		ownerAccountRepository.save(owner);
		if (firstApprovedOwner) {
			jdbcTemplate.update("UPDATE residents SET owner_id = ? WHERE owner_id IS NULL", owner.getId());
		}
	}

	@Transactional(readOnly = true)
	public OwnerProfile getProfile(String username) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		return new OwnerProfile(owner.getUsername(), owner.getPgName(), owner.getLocation());
	}

	public void updateProfile(String username, ProfileUpdateRequest request) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		String updatedUsername = request.username().trim();
		if (!adminUsername.isBlank() && updatedUsername.equalsIgnoreCase(adminUsername)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is reserved");
		}
		ownerAccountRepository.findByUsername(updatedUsername)
				.filter(existingOwner -> !existingOwner.getId().equals(owner.getId()))
				.ifPresent(existingOwner -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already registered");
				});
		owner.updateUsername(updatedUsername);
		owner.updatePgName(request.pgName().trim());
		owner.updateLocation(request.location().trim());
		ownerAccountRepository.save(owner);
	}

	public record OwnerProfile(String username, String pgName, String location) {
	}

	public record OwnerSummary(Long id, String username, String pgName, String mobileNumber, String location) {
	}

	public record OwnerOverview(List<OwnerSummary> pending, List<OwnerSummary> approved) {
	}

	public record ProfileUpdateRequest(
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 3, max = 50)
			@jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String pgName,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 150) String location) {
	}

	private long elapsedMs(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
