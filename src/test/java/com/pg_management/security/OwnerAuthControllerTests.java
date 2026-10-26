package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerAuthController.class)
@Import(SecurityConfig.class)
class OwnerAuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerAuthService ownerAuthService;

	@MockitoBean
	private OwnerUserDetailsService ownerUserDetailsService;

	@Test
	void ownerRegistrationNeedsNoOtpAndWaitsForAdminApproval() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "username": "new-owner",
								  "password": "a-long-owner-password",
								  "pgName": "Sample PG",
								  "location": "Madhapur, Hyderabad",
								  "mobileNumber": "9876543210"
								}
								"""))
				.andExpect(status().isAccepted());

		verify(ownerAuthService).registerOwner(
				"new-owner", "a-long-owner-password", "Sample PG", "Madhapur, Hyderabad", "9876543210");
		verifyNoMoreInteractions(ownerAuthService);
	}

	@Test
	void ownerRegistrationRejectsPasswordsShorterThanTwelveCharacters() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "username": "new-owner",
								  "password": "too-short",
								  "pgName": "Sample PG",
								  "location": "Madhapur, Hyderabad",
								  "mobileNumber": "9876543210"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerRegistrationRequiresLocation() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "username": "new-owner",
								  "password": "a-long-owner-password",
								  "pgName": "Sample PG",
								  "mobileNumber": "9876543210"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void errorDispatchDoesNotRequireAuthentication() throws Exception {
		mockMvc.perform(get("/error").with(request -> {
			request.setDispatcherType(DispatcherType.ERROR);
			return request;
		})).andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
	}
}
