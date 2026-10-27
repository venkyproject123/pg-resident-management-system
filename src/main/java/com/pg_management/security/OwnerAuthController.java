package com.pg_management.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
		ownerAuthService.registerOwner(
				request.username(),
				request.password(),
				request.pgName(),
				request.location(),
				request.mobileNumber());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/profile")
	public OwnerAuthService.OwnerProfile profile(Authentication authentication) {
		return ownerAuthService.getProfile(authentication.getName());
	}

	@PutMapping("/profile")
	public ResponseEntity<Void> updateProfile(
			Authentication authentication,
			@Valid @RequestBody OwnerAuthService.ProfileUpdateRequest request) {
		ownerAuthService.updateProfile(authentication.getName(), request);
		return ResponseEntity.noContent().build();
	}

	public record SetupStatus(boolean setupRequired) {
	}

	public record SetupRequest(
			@NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
			@NotBlank @Size(min = 12, max = 128) String password,
			@NotBlank @Size(max = 100) String pgName,
			@NotBlank @Size(max = 150) String location,
			@NotBlank @Pattern(regexp = "^(?:[6-9][0-9]{9}|\\+91[6-9][0-9]{9}|\\+[1-9][0-9]{7,14})$") String mobileNumber) {
	}

}
