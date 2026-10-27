package com.pg_management.security;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/owners")
public class AdminController {

	private final OwnerAuthService ownerAuthService;

	public AdminController(OwnerAuthService ownerAuthService) {
		this.ownerAuthService = ownerAuthService;
	}

	@GetMapping
	public OwnerAuthService.OwnerOverview ownersOverview() {
		return ownerAuthService.findOwnersOverview();
	}

	@GetMapping("/pending")
	public List<OwnerAuthService.OwnerSummary> pendingOwners() {
		return ownerAuthService.findPendingOwners();
	}

	@GetMapping("/approved")
	public List<OwnerAuthService.OwnerSummary> approvedOwners() {
		return ownerAuthService.findApprovedOwners();
	}

	@PutMapping("/{ownerId}/approve")
	public ResponseEntity<Void> approveOwner(@PathVariable Long ownerId) {
		ownerAuthService.approveOwner(ownerId);
		return ResponseEntity.noContent().build();
	}
}
