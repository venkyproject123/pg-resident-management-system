package com.pg_management.security;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerPasswordRecoveryController.class)
@Import(SecurityConfig.class)
class OwnerPasswordRecoveryControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerPasswordRecoveryService passwordRecoveryService;

	@MockitoBean
	private OwnerUserDetailsService ownerUserDetailsService;

	@Test
	void resetUsesRecoveryIdWithoutAnEmailOrOtp() throws Exception {
		mockMvc.perform(post("/api/owner/password-recovery/reset")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recoveryId": "ABCDEFGHJKLMNPQR",
								  "newPassword": "a-new-long-owner-password",
								  "confirmPassword": "a-new-long-owner-password"
								}
								"""))
				.andExpect(status().isNoContent());

		verify(passwordRecoveryService).resetPassword("ABCDEFGHJKLMNPQR", "a-new-long-owner-password");
	}

	@Test
	void resetRejectsMismatchedPasswords() throws Exception {
		mockMvc.perform(post("/api/owner/password-recovery/reset")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recoveryId": "ABCDEFGHJKLMNPQR",
								  "newPassword": "a-new-long-owner-password",
								  "confirmPassword": "a-different-long-password"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void resetRejectsInvalidRecoveryIdFormat() throws Exception {
		mockMvc.perform(post("/api/owner/password-recovery/reset")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "recoveryId": "12345678",
								  "newPassword": "a-new-long-owner-password",
								  "confirmPassword": "a-new-long-owner-password"
								}
								"""))
				.andExpect(status().isBadRequest());
	}
}
