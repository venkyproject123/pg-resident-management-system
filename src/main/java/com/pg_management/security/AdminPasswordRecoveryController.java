package com.pg_management.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/password-recovery")
public class AdminPasswordRecoveryController {

	private final AdminPasswordRecoveryService recoveryService;

	public AdminPasswordRecoveryController(AdminPasswordRecoveryService recoveryService) {
		this.recoveryService = recoveryService;
	}

	@PostMapping("/request")
	public ResponseEntity<Void> requestRecoveryId(@Valid @RequestBody RecoveryRequest request) {
		recoveryService.generateRecoveryId(request.email());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/reset")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
		recoveryService.resetPassword(request.email(), request.recoveryId(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	public record RecoveryRequest(@NotBlank @Email @Size(max = 100) String email) {
	}

	public record PasswordResetRequest(
			@NotBlank @Email @Size(max = 100) String email,
			@NotBlank @Size(min = 16, max = 19) String recoveryId,
			@NotBlank @Size(min = 12, max = 128) String newPassword) {
	}
}
