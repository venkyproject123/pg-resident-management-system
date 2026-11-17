package com.pg_management.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
	void ownerRegistrationIsAcceptedAndWaitsForAdminApproval() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "ownerName": "Owner Name",
								  "mobileNumber": "9876543210",
								  "email": "owner@example.com",
								  "pgName": "Sample PG",
								  "propertyType": "BOYS",
								  "locality": "Madhapur",
								  "city": "Hyderabad",
								  "password": "a-long-owner-password"
								}
								"""))
				.andExpect(status().isAccepted());

		verify(ownerAuthService).registerOwner(
				"Owner Name",
				"9876543210",
				"owner@example.com",
				"Sample PG",
				"BOYS",
				"Madhapur",
				"Hyderabad",
				"a-long-owner-password");
		verifyNoMoreInteractions(ownerAuthService);
	}

	@Test
	void ownerRegistrationRejectsPasswordsShorterThanTwelveCharacters() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "ownerName": "Owner Name",
								  "mobileNumber": "9876543210",
								  "email": "owner@example.com",
								  "pgName": "Sample PG",
								  "propertyType": "BOYS",
								  "locality": "Madhapur",
								  "city": "Hyderabad",
								  "password": "too-short"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerRegistrationRequiresAllPropertyDetails() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "email": "owner@example.com",
								  "password": "a-long-owner-password"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerRegistrationRejectsInvalidEmail() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "ownerName": "Owner Name",
								  "mobileNumber": "9876543210",
								  "email": "not-an-email",
								  "pgName": "Sample PG",
								  "propertyType": "BOYS",
								  "locality": "Madhapur",
								  "city": "Hyderabad",
								  "password": "a-long-owner-password"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void ownerRegistrationRejectsUnsupportedOccupancyTypes() throws Exception {
		mockMvc.perform(post("/api/owner/setup")
						.contentType("application/json")
						.content("""
								{
								  "ownerName": "Owner Name",
								  "mobileNumber": "9876543210",
								  "email": "owner@example.com",
								  "pgName": "Sample PG",
								  "propertyType": "FAMILY",
								  "locality": "Madhapur",
								  "city": "Hyderabad",
								  "password": "a-long-owner-password"
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
