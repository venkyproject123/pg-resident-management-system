package com.pg_management.resident;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.pg_management.resident.dto.ResidentResponse;
import com.pg_management.resident.storage.ResidentDocument;
import com.pg_management.security.OwnerUserDetailsService;
import com.pg_management.security.OwnerAccount;
import com.pg_management.security.OwnerAccountRepository;
import com.pg_management.security.SecurityConfig;

@WebMvcTest(
		value = ResidentController.class,
		properties = {
				"spring.security.user.name=test-owner",
				"spring.security.user.password=test-password"
		})
@Import(SecurityConfig.class)
class ResidentControllerTests {

	private static final String AUTHORIZATION = "Basic " + Base64.getEncoder().encodeToString(
			"test-owner:test-password".getBytes(StandardCharsets.UTF_8));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ResidentService residentService;

	@MockitoBean
	private RentPaymentService rentPaymentService;

	@MockitoBean
	private OwnerUserDetailsService ownerUserDetailsService;

	@MockitoBean
	private OwnerAccountRepository ownerAccountRepository;

	@BeforeEach
	void configureOwnerCredentials() {
		when(ownerUserDetailsService.loadUserByUsername("test-owner"))
				.thenReturn(User.withUsername("test-owner")
						.password(new BCryptPasswordEncoder(4).encode("test-password"))
						.roles("OWNER")
						.build());
		when(ownerAccountRepository.findByUsername("test-owner"))
				.thenReturn(Optional.of(new OwnerAccount("test-owner", "encoded-password")));
	}

	@Test
	void residentEndpointsRequireAuthentication() throws Exception {
		mockMvc.perform(get("/api/residents/get"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void createResidentReturnsCreatedLocationForAuthenticatedOwner() throws Exception {
		when(residentService.create(any(), any(OwnerAccount.class))).thenReturn(sampleResident());

		mockMvc.perform(post("/api/residents/post")
						.header("Authorization", AUTHORIZATION)
						.contentType("application/json")
						.content("""
								{
								  "name": "Sample Resident",
								  "fatherName": "Sample Father",
								  "mobileNumber": "+919876543210",
								  "roomNumber": "A-101",
								  "sharing": 2,
								  "rent": 12000.00,
								  "advanceAmount": 24000.00,
								  "joiningDate": "2026-10-01",
								  "permanentAddress": "Sample permanent address",
								  "companyAddress": "Sample company address"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/residents/7"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.sharing").value(2));
	}

	@Test
	void createResidentRequiresSharingCapacity() throws Exception {
		mockMvc.perform(post("/api/residents/post")
						.header("Authorization", AUTHORIZATION)
						.contentType("application/json")
						.content("""
								{
								  "name": "Sample Resident",
								  "fatherName": "Sample Father",
								  "mobileNumber": "+919876543210",
								  "roomNumber": "A-101",
								  "rent": 12000.00,
								  "advanceAmount": 24000.00,
								  "joiningDate": "2026-10-01",
								  "permanentAddress": "Sample permanent address",
								  "companyAddress": "Sample company address"
								}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listResidentsReturnsDataForAuthenticatedOwner() throws Exception {
		when(residentService.findAll(any(OwnerAccount.class))).thenReturn(List.of(sampleResident()));

		mockMvc.perform(get("/api/residents/get").header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Sample Resident"));
	}

	@Test
	void listResidentsByStatusReturnsDataForAuthenticatedOwner() throws Exception {
		when(residentService.findByStatus(any(OwnerAccount.class), eq(ResidentStatus.VACATED))).thenReturn(List.of(sampleResident()));

		mockMvc.perform(get("/api/residents/status/VACATED").header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Sample Resident"));
	}

	@Test
	void searchResidentsReturnsMatchesForAuthenticatedOwner() throws Exception {
		when(residentService.search(any(OwnerAccount.class), eq("A-101"), eq(null))).thenReturn(List.of(sampleResident()));

		mockMvc.perform(get("/api/residents/search")
						.param("keyword", "A-101")
						.header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].roomNumber").value("A-101"));
	}

	@Test
	void searchResidentsCanBeLimitedToSelectedStatus() throws Exception {
		when(residentService.search(any(OwnerAccount.class), eq("101"), eq(ResidentStatus.VACATED))).thenReturn(List.of(sampleResident()));

		mockMvc.perform(get("/api/residents/search")
						.param("keyword", "101")
						.param("status", "VACATED")
						.header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].roomNumber").value("A-101"));
	}

	@Test
	void findResidentByIdReturnsDataForAuthenticatedOwner() throws Exception {
		when(residentService.findById(any(OwnerAccount.class), eq(7L))).thenReturn(sampleResident());

		mockMvc.perform(get("/api/residents/7").header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.name").value("Sample Resident"));
	}

	@Test
	void updateResidentReturnsUpdatedDataForAuthenticatedOwner() throws Exception {
		when(residentService.update(any(OwnerAccount.class), eq(7L), any())).thenReturn(sampleResident());

		mockMvc.perform(put("/api/residents/7")
						.header("Authorization", AUTHORIZATION)
						.contentType("application/json")
						.content("""
								{
								  "name": "Sample Resident",
								  "fatherName": "Sample Father",
								  "mobileNumber": "+919876543210",
								  "roomNumber": "A-101",
								  "sharing": 2,
								  "rent": 12000.00,
								  "advanceAmount": 24000.00,
								  "joiningDate": "2026-10-01",
								  "permanentAddress": "Sample permanent address",
								  "companyAddress": "Sample company address"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(7));
	}

	@Test
	void deleteResidentMarksItVacatedForAuthenticatedOwner() throws Exception {
		mockMvc.perform(delete("/api/residents/7").header("Authorization", AUTHORIZATION))
				.andExpect(status().isNoContent());

		verify(residentService).markVacated(any(OwnerAccount.class), eq(7L));
	}

	@Test
	void permanentlyDeleteResidentRequiresOwnerAuthentication() throws Exception {
		mockMvc.perform(delete("/api/residents/7/permanent"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void permanentlyDeleteResidentRemovesRecordForAuthenticatedOwner() throws Exception {
		mockMvc.perform(delete("/api/residents/7/permanent").header("Authorization", AUTHORIZATION))
				.andExpect(status().isNoContent());

		verify(residentService).permanentlyDelete(any(OwnerAccount.class), eq(7L));
	}

	@Test
	void uploadingPhotoRequiresOwnerAuthentication() throws Exception {
		mockMvc.perform(multipart("/api/residents/7/photo")
						.file(new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1})))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void viewingPhotoRequiresOwnerAuthentication() throws Exception {
		mockMvc.perform(get("/api/residents/7/photo"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedOwnerCanUploadPhoto() throws Exception {
		mockMvc.perform(multipart("/api/residents/7/photo")
						.file(new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1, 2, 3}))
						.header("Authorization", AUTHORIZATION))
				.andExpect(status().isNoContent());

		verify(residentService).uploadPhoto(any(OwnerAccount.class), eq(7L), any());
	}

	@Test
	void privateDocumentResponseIsNotCacheable() throws Exception {
		when(residentService.getPhoto(any(OwnerAccount.class), eq(7L)))
				.thenReturn(new ResidentDocument(new byte[] {1, 2, 3}, MediaType.IMAGE_PNG));

		mockMvc.perform(get("/api/residents/7/photo").header("Authorization", AUTHORIZATION))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "private, no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"));
	}

	private ResidentResponse sampleResident() {
		return new ResidentResponse(
				7L,
				"Sample Resident",
				"Sample Father",
				"+919876543210",
				"A-101",
				2,
				new BigDecimal("12000.00"),
				new BigDecimal("24000.00"),
				LocalDate.of(2026, 10, 1),
				null,
				"Sample permanent address",
				"Sample company address",
				ResidentStatus.ACTIVE,
				false,
				false);
	}
}
