package com.pg_management.security;

import java.util.List;

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

	private final OwnerAccountRepository ownerAccountRepository;
	private final OwnerOtpService ownerOtpService;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;
	private final String adminUsername;

	public OwnerAuthService(
			OwnerAccountRepository ownerAccountRepository,
			OwnerOtpService ownerOtpService,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate,
			@Value("${app.admin.username:}") String adminUsername) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.ownerOtpService = ownerOtpService;
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
			String mobileNumber) {
		if (!adminUsername.isBlank() && username.equalsIgnoreCase(adminUsername)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is reserved");
		}
		if (ownerAccountRepository.findByUsername(username).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already registered");
		}
		String normalizedNumber = mobileNumber.matches("[6-9][0-9]{9}") ? "+91" + mobileNumber : mobileNumber;
		if (ownerAccountRepository.findByWhatsappNumber(normalizedNumber).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That mobile number is already registered");
		}
		ownerAccountRepository.saveAndFlush(new OwnerAccount(
				username, passwordEncoder.encode(password), pgName.trim(), normalizedNumber, false));
	}

	@Transactional(readOnly = true)
	public List<OwnerSummary> findPendingOwners() {
		return ownerAccountRepository.findAllByApprovedFalseOrderByIdAsc().stream()
				.map(owner -> new OwnerSummary(
						owner.getId(), owner.getUsername(), owner.getPgName(), owner.getWhatsappNumber()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<OwnerSummary> findApprovedOwners() {
		return ownerAccountRepository.findAllByApprovedTrueOrderByIdAsc().stream()
				.map(owner -> new OwnerSummary(
						owner.getId(), owner.getUsername(), owner.getPgName(), owner.getWhatsappNumber()))
				.toList();
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

	public void requestPasswordResetOtp(String username, String whatsappNumber, String requesterAddress) {
		ownerOtpService.requestPasswordResetOtp(username, whatsappNumber, requesterAddress);
	}

	@Transactional(readOnly = true)
	public OwnerProfile getProfile(String username) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		return new OwnerProfile(owner.getUsername(), owner.getPgName(), owner.getWhatsappNumber());
	}

	public void requestProfileOtp(String username, String whatsappNumber, String requesterAddress) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		ownerOtpService.requestProfileUpdateOtp(owner.getId(), whatsappNumber, requesterAddress);
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
		ownerOtpService.updateOwnerProfile(
				owner.getId(), request.pgName(), request.whatsappNumber(), request.otp());
		owner.updateUsername(updatedUsername);
		ownerAccountRepository.save(owner);
	}

	public void resetPassword(String username, String whatsappNumber, String otp, String newPassword) {
		ownerOtpService.resetPassword(username, whatsappNumber, otp, newPassword);
	}

	public record OwnerProfile(String username, String pgName, String whatsappNumber) {
	}

	public record OwnerSummary(Long id, String username, String pgName, String mobileNumber) {
	}

	public record ProfileUpdateRequest(
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 3, max = 50)
			@jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String pgName,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^(?:[6-9][0-9]{9}|\\+91[6-9][0-9]{9}|\\+[1-9][0-9]{7,14})$") String whatsappNumber,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^[0-9]{6}$") String otp) {
	}
}
