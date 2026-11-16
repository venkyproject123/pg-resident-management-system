package com.pg_management.security;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/owner/otp")
@Validated
public class OwnerOtpController {

	private final OtpLoginService otpLoginService;

	public OwnerOtpController(OtpLoginService otpLoginService) {
		this.otpLoginService = otpLoginService;
	}

	@PostMapping("/request")
	public ResponseEntity<Void> request(@Valid @RequestBody OtpRequest request) {
		otpLoginService.requestOtp(request.mobileNumber());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/verify")
	public OtpLoginService.OtpSession verify(@Valid @RequestBody OtpVerification request) {
		return otpLoginService.verifyOtp(request.mobileNumber(), request.code());
	}

	public record OtpRequest(@NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$") String mobileNumber) {
	}

	public record OtpVerification(
			@NotBlank @Pattern(regexp = "^[6-9][0-9]{9}$") String mobileNumber,
			@NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
	}
}
