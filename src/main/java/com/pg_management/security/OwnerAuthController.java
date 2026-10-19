package com.pg_management.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/owner")
@Validated
public class OwnerAuthController {

	private final OwnerAuthService ownerAuthService;

	public OwnerAuthController(OwnerAuthService ownerAuthService) {
		this.ownerAuthService = ownerAuthService;
	}

	@GetMapping("/setup-status")
	public SetupStatus setupStatus() {
		return new SetupStatus(ownerAuthService.setupRequired());
	}

	@PostMapping("/setup")
	public ResponseEntity<Void> setup(@Valid @RequestBody SetupRequest request) {
		ownerAuthService.setupOwner(
				request.setupToken(),
				request.username(),
				request.password());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/password-reset")
	public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetRequest request) {
		ownerAuthService.resetPassword(request.recoveryToken(), request.username(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/password/change")
	public ResponseEntity<Void> changePassword(
			Authentication authentication,
			@Valid @RequestBody PasswordChange request) {
		ownerAuthService.changePassword(authentication.getName(), request.currentPassword(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	public record SetupStatus(boolean setupRequired) {
	}

	public record SetupRequest(
			@NotBlank String setupToken,
			@NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
			@NotBlank @Size(min = 12, max = 128) String password) {
	}

	public record ResetRequest(
			@NotBlank String recoveryToken,
			@NotBlank String username,
			@NotBlank @Size(min = 12, max = 128) String newPassword) {
	}

	public record PasswordChange(
			@NotBlank String currentPassword,
			@NotBlank @Size(min = 12, max = 128) String newPassword) {
	}
}
