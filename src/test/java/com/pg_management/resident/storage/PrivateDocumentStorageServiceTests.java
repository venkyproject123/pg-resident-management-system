package com.pg_management.resident.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PrivateDocumentStorageServiceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void storesAndLoadsPdfWithGeneratedPrivateKey() throws Exception {
		PrivateDocumentStorageService storage = new PrivateDocumentStorageService(temporaryDirectory.toString());
		byte[] pdfBytes = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);

		String storageKey = storage.store(
				new MockMultipartFile("file", "identity.pdf", "application/pdf", pdfBytes),
				ResidentDocumentType.IDENTITY_PROOF);
		ResidentDocument document = storage.load(storageKey);

		assertEquals("application/pdf", document.mediaType().toString());
		assertArrayEquals(pdfBytes, document.content());
		try (var files = Files.list(temporaryDirectory)) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void rejectsPhotoWithUnsupportedContent() {
		PrivateDocumentStorageService storage = new PrivateDocumentStorageService(temporaryDirectory.toString());
		MockMultipartFile fakeImage =
				new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> storage.store(fakeImage, ResidentDocumentType.PHOTO));

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatusCode());
	}

	@Test
	void rejectsFilesLargerThanFiveMegabytes() {
		PrivateDocumentStorageService storage = new PrivateDocumentStorageService(temporaryDirectory.toString());
		byte[] large = new byte[(int) PrivateDocumentStorageService.MAX_FILE_SIZE + 1];
		MockMultipartFile upload = new MockMultipartFile("file", "photo.png", "image/png", large);

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> storage.store(upload, ResidentDocumentType.PHOTO));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
	}
}
