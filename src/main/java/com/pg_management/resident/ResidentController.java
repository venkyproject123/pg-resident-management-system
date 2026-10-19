package com.pg_management.resident;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pg_management.resident.dto.CreateResidentRequest;
import com.pg_management.resident.dto.ResidentResponse;
import com.pg_management.resident.dto.UpdateResidentRequest;
import com.pg_management.resident.storage.ResidentDocument;
import com.pg_management.security.OwnerAccount;
import com.pg_management.security.OwnerAccountRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/residents")
@Validated
public class ResidentController {

	private final ResidentService residentService;
	private final OwnerAccountRepository ownerAccountRepository;

	public ResidentController(ResidentService residentService, OwnerAccountRepository ownerAccountRepository) {
		this.residentService = residentService;
		this.ownerAccountRepository = ownerAccountRepository;
	}

	@PostMapping("/post")
	public ResponseEntity<ResidentResponse> create(
			Authentication authentication,
			@Valid @RequestBody CreateResidentRequest request) {
		ResidentResponse resident = residentService.create(request, owner(authentication));
		return ResponseEntity.created(URI.create("/api/residents/" + resident.id())).body(resident);
	}

	@GetMapping("/get")
	public List<ResidentResponse> findAll(Authentication authentication) {
		return residentService.findAll(owner(authentication));
	}

	@GetMapping("/all")
	public List<ResidentResponse> findAllMembers(Authentication authentication) {
		return residentService.findAllMembers(owner(authentication));
	}

	@GetMapping("/status/{status}")
	public List<ResidentResponse> findByStatus(Authentication authentication, @PathVariable ResidentStatus status) {
		return residentService.findByStatus(owner(authentication), status);
	}

	@GetMapping("/search")
	public List<ResidentResponse> search(
			Authentication authentication,
			@RequestParam @NotBlank String keyword,
			@RequestParam(required = false) ResidentStatus status) {
		return residentService.search(owner(authentication), keyword, status);
	}

	@GetMapping("/{id}")
	public ResidentResponse findById(Authentication authentication, @PathVariable Long id) {
		return residentService.findById(owner(authentication), id);
	}

	@PutMapping("/{id}")
	public ResidentResponse update(
			Authentication authentication,
			@PathVariable Long id,
			@Valid @RequestBody UpdateResidentRequest request) {
		return residentService.update(owner(authentication), id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> markVacated(Authentication authentication, @PathVariable Long id) {
		residentService.markVacated(owner(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}/permanent")
	public ResponseEntity<Void> permanentlyDelete(Authentication authentication, @PathVariable Long id) {
		residentService.permanentlyDelete(owner(authentication), id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/photo")
	public ResponseEntity<Void> uploadPhoto(
			Authentication authentication, @PathVariable Long id, @RequestPart("file") MultipartFile file) {
		residentService.uploadPhoto(owner(authentication), id, file);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/identity-proof")
	public ResponseEntity<Void> uploadIdentityProof(
			Authentication authentication, @PathVariable Long id, @RequestPart("file") MultipartFile file) {
		residentService.uploadIdentityProof(owner(authentication), id, file);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/photo")
	public ResponseEntity<byte[]> getPhoto(Authentication authentication, @PathVariable Long id) {
		return documentResponse(residentService.getPhoto(owner(authentication), id));
	}

	@GetMapping("/{id}/identity-proof")
	public ResponseEntity<byte[]> getIdentityProof(Authentication authentication, @PathVariable Long id) {
		return documentResponse(residentService.getIdentityProof(owner(authentication), id));
	}

	private OwnerAccount owner(Authentication authentication) {
		return ownerAccountRepository.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
	}

	private ResponseEntity<byte[]> documentResponse(ResidentDocument document) {
		return ResponseEntity.ok()
				.contentType(document.mediaType())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.header("X-Content-Type-Options", "nosniff")
				.body(document.content());
	}
}
