package com.pg_management.security;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OtpLoginService {

	private static final Logger LOGGER = LoggerFactory.getLogger(OtpLoginService.class);
	private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
	private static final Duration RESEND_DELAY = Duration.ofSeconds(60);
	private static final Duration TOKEN_LIFETIME = Duration.ofHours(8);
	private static final int MAX_ATTEMPTS = 5;

	private final OwnerAccountRepository ownerAccountRepository;
	private final String accountSid;
	private final String authToken;
	private final String fromNumber;
	private final SecureRandom secureRandom = new SecureRandom();
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Map<String, OtpChallenge> challenges = new ConcurrentHashMap<>();
	private final Map<String, Instant> requestTimes = new ConcurrentHashMap<>();
	private final Map<String, AccessToken> accessTokens = new ConcurrentHashMap<>();

	public OtpLoginService(
			OwnerAccountRepository ownerAccountRepository,
			@Value("${app.twilio.account-sid:}") String accountSid,
			@Value("${app.twilio.auth-token:}") String authToken,
			@Value("${app.twilio.from-number:}") String fromNumber) {
		this.ownerAccountRepository = ownerAccountRepository;
		this.accountSid = accountSid;
		this.authToken = authToken;
		this.fromNumber = fromNumber;
	}

	public void requestOtp(String rawMobileNumber) {
		String mobileNumber = normalizeMobileNumber(rawMobileNumber);
		if (!isConfigured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Mobile OTP is not configured. Contact your administrator.");
		}
		Instant now = Instant.now();
		purgeExpiredState(now);
		AtomicBoolean rateLimited = new AtomicBoolean();
		requestTimes.compute(mobileNumber, (key, lastRequest) -> {
			if (lastRequest != null && lastRequest.plus(RESEND_DELAY).isAfter(now)) {
				rateLimited.set(true);
				return lastRequest;
			}
			return now;
		});
		if (rateLimited.get()) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Please wait one minute before requesting another OTP");
		}
		Optional<OwnerAccount> owner = ownerAccountRepository
				.findByMobileNumberAndApprovedTrueAndRemovedFalse(mobileNumber);
		if (owner.isEmpty()) return;
		String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
		sendSms(mobileNumber, "Your Haven sign-in code is " + code + ". It expires in 5 minutes. Do not share this code.");
		challenges.put(mobileNumber, new OtpChallenge(hash(code), Instant.now(), 0));
	}

	public OtpSession verifyOtp(String rawMobileNumber, String code) {
		String mobileNumber = normalizeMobileNumber(rawMobileNumber);
		OtpChallenge challenge = challenges.get(mobileNumber);
		if (challenge == null) throw invalidOtp();
		synchronized (challenge) {
			if (challenges.get(mobileNumber) != challenge) throw invalidOtp();
			if (challenge.sentAt().plus(OTP_LIFETIME).isBefore(Instant.now())) {
				challenges.remove(mobileNumber, challenge);
				throw invalidOtp();
			}
			if (challenge.attempts() >= MAX_ATTEMPTS) throw invalidOtp();
			if (!MessageDigest.isEqual(challenge.codeHash(), hash(code))) {
				challenges.replace(mobileNumber, challenge,
						new OtpChallenge(challenge.codeHash(), challenge.sentAt(), challenge.attempts() + 1));
				throw invalidOtp();
			}
			OwnerAccount owner = ownerAccountRepository.findByMobileNumberAndApprovedTrueAndRemovedFalse(mobileNumber)
					.orElseThrow(this::invalidOtp);
			challenges.remove(mobileNumber, challenge);
			purgeExpiredState(Instant.now());
			String token = randomToken();
			accessTokens.put(token, new AccessToken(owner.getId(), Instant.now().plus(TOKEN_LIFETIME)));
			return new OtpSession(token, TOKEN_LIFETIME.toSeconds());
		}
	}

	public Optional<OwnerAccount> ownerForAccessToken(String token) {
		AccessToken accessToken = accessTokens.get(token);
		if (accessToken == null || accessToken.expiresAt().isBefore(Instant.now())) {
			accessTokens.remove(token);
			return Optional.empty();
		}
		return ownerAccountRepository.findById(accessToken.ownerId())
				.filter(owner -> owner.isApproved() && !owner.isRemoved());
	}

	private void sendSms(String to, String message) {
		try {
			String body = "To=" + encode(to) + "&From=" + encode(fromNumber) + "&Body=" + encode(message);
			String basic = Base64.getEncoder().encodeToString(
					(accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
					.timeout(Duration.ofSeconds(15))
					.header("Authorization", "Basic " + basic)
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body))
					.build();
			HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				LOGGER.warn("Twilio OTP delivery failed with HTTP status {}", response.statusCode());
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the OTP. Try again shortly.");
			}
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			LOGGER.warn("Twilio OTP delivery was interrupted");
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not send the OTP. Try again shortly.");
		} catch (Exception exception) {
			LOGGER.warn("Twilio OTP delivery failed", exception);
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY, "Could not send the OTP. Try again shortly.", exception);
		}
	}

	private boolean isConfigured() {
		return !accountSid.isBlank() && !authToken.isBlank() && !fromNumber.isBlank();
	}

	private void purgeExpiredState(Instant now) {
		challenges.entrySet().removeIf(entry ->
				entry.getValue().sentAt().plus(OTP_LIFETIME).isBefore(now));
		requestTimes.entrySet().removeIf(entry -> entry.getValue().plus(OTP_LIFETIME).isBefore(now));
		accessTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
	}

	private String normalizeMobileNumber(String rawMobileNumber) {
		if (rawMobileNumber == null || !rawMobileNumber.matches("[6-9][0-9]{9}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number");
		}
		return "+91" + rawMobileNumber;
	}

	private ResponseStatusException invalidOtp() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The code is invalid or has expired");
	}

	private byte[] hash(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		} catch (Exception exception) {
			throw new IllegalStateException("OTP hashing is unavailable", exception);
		}
	}

	private String randomToken() {
		byte[] bytes = new byte[32];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	public record OtpSession(String accessToken, long expiresInSeconds) {
	}

	private record OtpChallenge(byte[] codeHash, Instant sentAt, int attempts) {
	}

	private record AccessToken(Long ownerId, Instant expiresAt) {
	}
}
