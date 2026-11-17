package com.pg_management.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/owner/password-recovery")
@Validated
public class OwnerPasswordRecoveryController {

	private final OwnerPasswordRecoveryService passwordRecoveryService;

	public OwnerPasswordRecoveryController(OwnerPasswordRecoveryService passwordRecoveryService) {
		this.passwordRecoveryService = passwordRecoveryService;
	}

	@PostMapping("/reset")
	public ResponseEntity<Void> reset(@Valid @RequestBody PasswordResetRequest request) {
		if (!request.newPassword().equals(request.confirmPassword())) {
			throw new org.springframework.web.server.ResponseStatusException(
					HttpStatus.BAD_REQUEST, "The passwords do not match");
		}
		passwordRecoveryService.resetPassword(request.recoveryId(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	public record PasswordResetRequest(
			@NotBlank @Pattern(regexp = "^[A-HJ-NP-Z2-9-]{16,19}$",
					message = "must be a valid recovery ID") String recoveryId,
			@NotBlank @Size(min = 12, max = 128) String newPassword,
			@NotBlank @Size(min = 12, max = 128) String confirmPassword) {
	}
}
