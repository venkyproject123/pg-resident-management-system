package com.pg_management.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OwnerOtpController.class)
@Import(SecurityConfig.class)
class OwnerOtpControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OtpLoginService otpLoginService;

	@MockitoBean
	private OwnerUserDetailsService ownerUserDetailsService;

	@Test
	void otpRequestAcceptsAValidIndianMobileNumber() throws Exception {
		mockMvc.perform(post("/api/owner/otp/request")
						.contentType("application/json")
						.content("""
								{"mobileNumber":"9876543210"}
								"""))
				.andExpect(status().isNoContent());

		verify(otpLoginService).requestOtp("9876543210");
	}

	@Test
	void otpRequestRejectsAnInvalidMobileNumber() throws Exception {
		mockMvc.perform(post("/api/owner/otp/request")
						.contentType("application/json")
						.content("""
								{"mobileNumber":"5876543210"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void otpVerificationReturnsTheIssuedSession() throws Exception {
		when(otpLoginService.verifyOtp("9876543210", "123456"))
				.thenReturn(new OtpLoginService.OtpSession("test-access-token", 28_800));

		mockMvc.perform(post("/api/owner/otp/verify")
						.contentType("application/json")
						.content("""
								{"mobileNumber":"9876543210","code":"123456"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("test-access-token"))
				.andExpect(jsonPath("$.expiresInSeconds").value(28_800));

		verify(otpLoginService).verifyOtp("9876543210", "123456");
	}
}
