package com.pg_management.security;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.pg_management.resident.storage.ResidentDocument;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
				request.ownerName(),
				request.mobileNumber(),
				request.email(),
				request.pgName(),
				request.propertyType(),
				request.locality(),
				request.city(),
				request.password());
		return ResponseEntity.accepted().build();
	}

	@GetMapping("/profile")
	public OwnerAuthService.OwnerProfile profile(Authentication authentication) {
		return ownerAuthService.getProfile(authentication.getName());
	}

	@PutMapping("/profile")
	public OwnerAuthService.OwnerProfile updateProfile(
			Authentication authentication,
			@Valid @RequestBody ProfileUpdateRequest request) {
		return ownerAuthService.updateProfile(
				authentication.getName(),
				request.ownerName(),
				request.mobileNumber(),
				request.pgName(),
				request.propertyType(),
				request.locality(),
				request.city());
	}

	@PostMapping("/profile/photo")
	public ResponseEntity<Void> uploadProfilePhoto(
			Authentication authentication, @RequestPart("file") MultipartFile file) {
		ownerAuthService.uploadProfilePhoto(authentication.getName(), file);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/profile/photo")
	public ResponseEntity<byte[]> profilePhoto(Authentication authentication) {
		ResidentDocument document = ownerAuthService.getProfilePhoto(authentication.getName());
		return ResponseEntity.ok()
				.contentType(document.mediaType())
				.cacheControl(CacheControl.noStore())
				.header("X-Content-Type-Options", "nosniff")
				.body(document.content());
	}

	public record SetupStatus(boolean setupRequired) {
	}

	public record SetupRequest(
			@NotBlank @Size(max = 100) String ownerName,
			@NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number")
			String mobileNumber,
			@NotBlank @jakarta.validation.constraints.Email @Size(max = 100) String email,
			@NotBlank @Size(max = 100) String pgName,
			@NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^(?i:CO_LIVING|BOYS|GIRLS)$", message = "must be co-living, boys, or girls")
			String propertyType,
			@NotBlank @Size(max = 100) String locality,
			@NotBlank @Size(max = 100) String city,
			@NotBlank @Size(min = 12, max = 128) String password) {
	}

	public record ProfileUpdateRequest(
			@NotBlank @Size(max = 100) String ownerName,
			@NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^[6-9][0-9]{9}$", message = "must be a 10-digit Indian mobile number")
			String mobileNumber,
			@NotBlank @Size(max = 100) String pgName,
			@NotBlank @jakarta.validation.constraints.Pattern(
					regexp = "^(?i:CO_LIVING|BOYS|GIRLS)$", message = "must be co-living, boys, or girls")
			String propertyType,
			@NotBlank @Size(max = 100) String locality,
			@NotBlank @Size(max = 100) String city) {
	}

}
