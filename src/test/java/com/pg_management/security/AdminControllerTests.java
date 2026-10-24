package com.pg_management.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTests {

	private static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString(
			"admin:admin-password".getBytes(StandardCharsets.UTF_8));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerAuthService ownerAuthService;

	@MockitoBean
	private OwnerUserDetailsService ownerUserDetailsService;

	@BeforeEach
	void configureAdminCredentials() {
		when(ownerUserDetailsService.loadUserByUsername("admin"))
				.thenReturn(User.withUsername("admin")
						.password(new BCryptPasswordEncoder(4).encode("admin-password"))
						.roles("ADMIN")
						.build());
	}

	@Test
	void pendingOwnerListRequiresAdminAuthentication() throws Exception {
		mockMvc.perform(get("/api/admin/owners/pending"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void adminCanApproveRegistration() throws Exception {
		mockMvc.perform(put("/api/admin/owners/7/approve").header("Authorization", AUTHORIZATION))
				.andExpect(status().isNoContent());

		verify(ownerAuthService).approveOwner(7L);
	}

	@Test
	void adminCanListPendingOwners() throws Exception {
		when(ownerAuthService.findPendingOwners()).thenReturn(List.of());

		mockMvc.perform(get("/api/admin/owners/pending").header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk());
	}

	@Test
	void approvedOwnerListRequiresAdminAuthentication() throws Exception {
		mockMvc.perform(get("/api/admin/owners/approved"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void adminCanListApprovedOwners() throws Exception {
		when(ownerAuthService.findApprovedOwners()).thenReturn(List.of());

		mockMvc.perform(get("/api/admin/owners/approved").header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk());
	}
}
