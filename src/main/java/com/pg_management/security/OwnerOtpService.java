package com.pg_management.security;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pg_management.whatsapp.WhatsAppCloudApiClient;

@Service
@Transactional(noRollbackFor = ResponseStatusException.class)
public class OwnerOtpService {

	private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
	private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
	private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
	private static final Duration RATE_WINDOW = Duration.ofHours(24);
	private static final int MAX_DAILY_OTP_COUNT = 5;
	private static final int MAX_FAILED_ATTEMPTS = 5;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final OwnerOtpChallengeRepository challengeRepository;
	private final OwnerAccountRepository ownerAccountRepository;
	private final PasswordEncoder passwordEncoder;
	private final WhatsAppCloudApiClient whatsAppClient;
	private final String adminWhatsappNumber;

	public OwnerOtpService(
			OwnerOtpChallengeRepository challengeRepository,
			OwnerAccountRepository ownerAccountRepository,
			PasswordEncoder passwordEncoder,
			WhatsAppCloudApiClient whatsAppClient,
			@Value("${app.whatsapp.admin-number:}") String adminWhatsappNumber) {
		this.challengeRepository = challengeRepository;
		this.ownerAccountRepository = ownerAccountRepository;
		this.passwordEncoder = passwordEncoder;
		this.whatsAppClient = whatsAppClient;
		this.adminWhatsappNumber = adminWhatsappNumber;
	}

	public void requestRegistrationOtp(String requesterAddress) {
		String number = getAdminWhatsappNumber();
		sendOtp(number, OwnerOtpPurpose.REGISTRATION, requesterAddress);
	}

	public void requestPasswordResetOtp(String username, String whatsappNumber, String requesterAddress) {
		String number = normalizeNumber(whatsappNumber);
		if (ownerAccountRepository.findByUsernameAndWhatsappNumber(username, number).isPresent()) {
			sendOtp(number, OwnerOtpPurpose.PASSWORD_RESET, requesterAddress);
		}
	}

	public void requestProfileUpdateOtp(Long ownerId, String whatsappNumber, String requesterAddress) {
		String number = normalizeNumber(whatsappNumber);
		ownerAccountRepository.findByWhatsappNumber(number)
				.filter(existingOwner -> !existingOwner.getId().equals(ownerId))
				.ifPresent(existingOwner -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "That WhatsApp number is already registered");
				});
		sendOtp(number, OwnerOtpPurpose.PROFILE_UPDATE, requesterAddress);
	}

	public void updateOwnerProfile(
			Long ownerId, String pgName, String whatsappNumber, String otp) {
		String number = normalizeNumber(whatsappNumber);
		OwnerAccount owner = ownerAccountRepository.findById(ownerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Owner account not found"));
		ownerAccountRepository.findByWhatsappNumber(number)
				.filter(existingOwner -> !existingOwner.getId().equals(ownerId))
				.ifPresent(existingOwner -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "That WhatsApp number is already registered");
				});
		consumeOtp(number, OwnerOtpPurpose.PROFILE_UPDATE, otp);
		owner.updateProfile(pgName.trim(), number);
		ownerAccountRepository.save(owner);
	}

	@Scheduled(cron = "0 0 * * * *")
	public void deleteExpiredChallenges() {
		challengeRepository.deleteByExpiresAtBefore(Instant.now());
	}

	public OwnerAccount registerOwner(
			String username,
			String password,
			String pgName,
			String whatsappNumber,
			String otp) {
		String number = normalizeNumber(whatsappNumber);
		if (ownerAccountRepository.findByUsername(username).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already registered");
		}
		if (ownerAccountRepository.findByWhatsappNumber(number).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That WhatsApp number is already registered");
		}
		consumeOtp(getAdminWhatsappNumber(), OwnerOtpPurpose.REGISTRATION, otp);
		OwnerAccount owner = ownerAccountRepository.saveAndFlush(new OwnerAccount(
				username, passwordEncoder.encode(password), pgName.trim(), number));
		return owner;
	}

	public void resetPassword(String username, String whatsappNumber, String otp, String newPassword) {
		String number = normalizeNumber(whatsappNumber);
		OwnerAccount owner = ownerAccountRepository.findByUsernameAndWhatsappNumber(username, number)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username, mobile number, or OTP is incorrect"));
		consumeOtp(number, OwnerOtpPurpose.PASSWORD_RESET, otp);
		owner.changePassword(passwordEncoder.encode(newPassword));
		ownerAccountRepository.save(owner);
	}

	private void sendOtp(String number, OwnerOtpPurpose purpose, String requesterAddress) {
		Instant now = Instant.now();
		String requesterKey = hashRequester(requesterAddress);
		if (challengeRepository.countByRequesterKeyAndCreatedAtAfter(requesterKey, now.minus(RATE_WINDOW)) >= 20) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"OTP request limit reached for this network. Try again later");
		}
		challengeRepository.findFirstByWhatsappNumberAndPurposeOrderByCreatedAtDesc(number, purpose)
				.filter(challenge -> challenge.getCreatedAt().plus(RESEND_DELAY).isAfter(now))
				.ifPresent(challenge -> {
					throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
							"Please wait one minute before requesting another OTP");
				});
		if (challengeRepository.countByWhatsappNumberAndPurposeAndCreatedAtAfter(
				number, purpose, now.minus(RATE_WINDOW)) >= MAX_DAILY_OTP_COUNT) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"OTP request limit reached. Try again tomorrow");
		}
		String code = String.format("%06d", RANDOM.nextInt(1_000_000));
		whatsAppClient.sendOtp(number, code);
		challengeRepository.deleteByWhatsappNumberAndPurpose(number, purpose);
		challengeRepository.save(new OwnerOtpChallenge(
				number, purpose, passwordEncoder.encode(code), requesterKey, now, now.plus(OTP_LIFETIME)));
	}

	private void consumeOtp(String number, OwnerOtpPurpose purpose, String providedCode) {
		OwnerOtpChallenge challenge = challengeRepository
				.findFirstByWhatsappNumberAndPurposeOrderByCreatedAtDesc(number, purpose)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is invalid or expired"));
		if (challenge.getExpiresAt().isBefore(Instant.now())
				|| challenge.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
			challengeRepository.delete(challenge);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is invalid or expired");
		}
		if (providedCode == null || !passwordEncoder.matches(providedCode, challenge.getCodeHash())) {
			challenge.recordFailedAttempt();
			challengeRepository.save(challenge);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is invalid or expired");
		}
		challengeRepository.delete(challenge);
	}

	private String normalizeNumber(String number) {
		if (number == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid Indian mobile number");
		}
		String normalized = number.trim();
		if (normalized.matches("[6-9][0-9]{9}")) {
			normalized = "+91" + normalized;
		}
		if (!PHONE_PATTERN.matcher(normalized).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid mobile number, such as 9876543210");
		}
		return normalized;
	}

	private String getAdminWhatsappNumber() {
		if (adminWhatsappNumber == null || adminWhatsappNumber.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Configure WHATSAPP_ADMIN_NUMBER in the backend environment before owner registration");
		}
		return normalizeNumber(adminWhatsappNumber);
	}

	private String hashRequester(String requesterAddress) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(String.valueOf(requesterAddress).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
