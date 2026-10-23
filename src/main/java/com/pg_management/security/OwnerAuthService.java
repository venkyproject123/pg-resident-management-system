package com.pg_management.security;

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

	public OwnerAuthService(
			OwnerAccountRepository ownerAccountRepository,
			OwnerOtpService ownerOtpService,
			PasswordEncoder passwordEncoder,
			JdbcTemplate jdbcTemplate) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.ownerOtpService = ownerOtpService;
		this.passwordEncoder = passwordEncoder;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public boolean setupRequired() {
		return ownerAccountRepository.count() == 0;
	}

	public void requestRegistrationOtp(String requesterAddress) {
		ownerOtpService.requestRegistrationOtp(requesterAddress);
	}

	public void registerOwner(
			String username,
			String password,
			String pgName,
			String whatsappNumber,
			String otp) {
		boolean firstOwner = ownerAccountRepository.count() == 0;
		OwnerAccount owner = ownerOtpService.registerOwner(
				username, password, pgName, whatsappNumber, otp);
		if (firstOwner) {
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
		return new OwnerProfile(owner.getPgName(), owner.getWhatsappNumber());
	}

	public void requestProfileOtp(String username, String whatsappNumber, String requesterAddress) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		ownerOtpService.requestProfileUpdateOtp(owner.getId(), whatsappNumber, requesterAddress);
	}

	public void updateProfile(String username, ProfileUpdateRequest request) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		ownerOtpService.updateOwnerProfile(
				owner.getId(), request.pgName(), request.whatsappNumber(), request.otp());
	}

	public void resetPassword(String username, String whatsappNumber, String otp, String newPassword) {
		ownerOtpService.resetPassword(username, whatsappNumber, otp, newPassword);
	}

	public void changePassword(String username, String currentPassword, String newPassword) {
		OwnerAccount owner = ownerAccountRepository.findByUsername(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		if (!passwordEncoder.matches(currentPassword, owner.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
		}
		owner.changePassword(passwordEncoder.encode(newPassword));
		ownerAccountRepository.save(owner);
	}

	public record OwnerProfile(String pgName, String whatsappNumber) {
	}

	public record ProfileUpdateRequest(
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100) String pgName,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^(?:[6-9][0-9]{9}|\\+91[6-9][0-9]{9}|\\+[1-9][0-9]{7,14})$") String whatsappNumber,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^[0-9]{6}$") String otp) {
	}
}
