package com.pg_management.whatsapp;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WhatsAppCloudApiClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppCloudApiClient.class);

	private final RestClient restClient;
	private final String phoneNumberId;
	private final String accessToken;
	private final String graphVersion;
	private final String language;
	private final String otpTemplate;
	private final String rentPaidTemplate;
	private final String rentReminderTemplate;

	public WhatsAppCloudApiClient(
			@Value("${app.whatsapp.api-url:https://graph.facebook.com}") String apiUrl,
			@Value("${app.whatsapp.graph-version:v22.0}") String graphVersion,
			@Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
			@Value("${app.whatsapp.access-token:}") String accessToken,
			@Value("${app.whatsapp.language:en_US}") String language,
			@Value("${app.whatsapp.templates.otp:}") String otpTemplate,
			@Value("${app.whatsapp.templates.rent-paid:}") String rentPaidTemplate,
			@Value("${app.whatsapp.templates.rent-reminder:}") String rentReminderTemplate) {
		this.restClient = RestClient.builder().baseUrl(apiUrl).build();
		this.phoneNumberId = phoneNumberId;
		this.accessToken = accessToken;
		this.graphVersion = graphVersion;
		this.language = language;
		this.otpTemplate = otpTemplate;
		this.rentPaidTemplate = rentPaidTemplate;
		this.rentReminderTemplate = rentReminderTemplate;
	}

	public void sendOtp(String recipient, String code) {
		sendAuthenticationTemplate(recipient, otpTemplate, code);
	}

	public void sendRentPaid(String recipient, String pgName, String residentName, String period, String amount, String method) {
		sendTemplate(recipient, rentPaidTemplate, List.of(pgName, residentName, period, amount, method));
	}

	public void sendRentReminder(String recipient, String pgName, String residentName, String period, String amount) {
		sendTemplate(recipient, rentReminderTemplate, List.of(pgName, residentName, period, amount));
	}

	private void sendTemplate(String recipient, String templateName, List<String> values) {
		if (accessToken.isBlank() || phoneNumberId.isBlank() || templateName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"WhatsApp Cloud API credentials and the required approved message template must be configured");
		}
		List<Map<String, Object>> parameters = values.stream()
				.map(value -> Map.<String, Object>of("type", "text", "text", value))
				.toList();
		Map<String, Object> template = Map.of(
				"name", templateName,
				"language", Map.of("code", language),
				"components", List.of(Map.of(
						"type", "body",
						"parameters", parameters)));
		Map<String, Object> payload = Map.of(
				"messaging_product", "whatsapp",
				"to", recipient.startsWith("+") ? recipient.substring(1) : recipient,
				"type", "template",
				"template", template);
		try {
			restClient.post()
					.uri("/{version}/{phoneNumberId}/messages", graphVersion, phoneNumberId)
					.header("Authorization", "Bearer " + accessToken)
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException exception) {
			LOGGER.error("WhatsApp Cloud API message delivery failed", exception);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "WhatsApp message delivery failed. Check the API configuration and approved template.", exception);
		}
	}

	private void sendAuthenticationTemplate(String recipient, String templateName, String code) {
		if (accessToken.isBlank() || phoneNumberId.isBlank() || templateName.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"WhatsApp Cloud API credentials and the approved authentication template must be configured");
		}
		Map<String, Object> codeParameter = Map.of("type", "text", "text", code);
		Map<String, Object> body = Map.of(
				"type", "body",
				"parameters", List.of(codeParameter));
		Map<String, Object> button = Map.of(
				"type", "button",
				"sub_type", "url",
				"index", "0",
				"parameters", List.of(codeParameter));
		Map<String, Object> template = Map.of(
				"name", templateName,
				"language", Map.of("code", language),
				"components", List.of(body, button));
		Map<String, Object> payload = Map.of(
				"messaging_product", "whatsapp",
				"to", recipient.startsWith("+") ? recipient.substring(1) : recipient,
				"type", "template",
				"template", template);
		try {
			restClient.post()
					.uri("/{version}/{phoneNumberId}/messages", graphVersion, phoneNumberId)
					.header("Authorization", "Bearer " + accessToken)
					.contentType(MediaType.APPLICATION_JSON)
					.body(payload)
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException exception) {
			LOGGER.error("WhatsApp authentication message delivery failed", exception);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"WhatsApp authentication message delivery failed. Check the API configuration and approved authentication template.",
					exception);
		}
	}
}
