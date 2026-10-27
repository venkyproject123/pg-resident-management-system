package com.pg_management.resident.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PrivateDocumentStorageServiceTests {

	@TempDir
	Path temporaryDirectory;

	@Test
	void storesAndLoadsPdfWithGeneratedPrivateKey() throws Exception {
		StoredResidentDocumentRepository repository = mock(StoredResidentDocumentRepository.class);
		when(repository.save(any(StoredResidentDocument.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		PrivateDocumentStorageService storage =
				new PrivateDocumentStorageService(temporaryDirectory.toString(), repository);
		byte[] pdfBytes = "%PDF-1.7 sample".getBytes(StandardCharsets.US_ASCII);

		String storageKey = storage.store(
				new MockMultipartFile("file", "identity.pdf", "application/pdf", pdfBytes),
				ResidentDocumentType.IDENTITY_PROOF);
		ArgumentCaptor<StoredResidentDocument> storedDocument = ArgumentCaptor.forClass(StoredResidentDocument.class);
		verify(repository).save(storedDocument.capture());
		when(repository.findById(storageKey)).thenReturn(Optional.of(storedDocument.getValue()));
		ResidentDocument document = storage.load(storageKey);

		assertEquals("application/pdf", document.mediaType().toString());
		assertArrayEquals(pdfBytes, document.content());
		try (var files = Files.list(temporaryDirectory)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void rejectsPhotoWithUnsupportedContent() {
		PrivateDocumentStorageService storage = new PrivateDocumentStorageService(
				temporaryDirectory.toString(), mock(StoredResidentDocumentRepository.class));
		MockMultipartFile fakeImage =
				new MockMultipartFile("file", "photo.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> storage.store(fakeImage, ResidentDocumentType.PHOTO));

		assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatusCode());
	}

	@Test
	void rejectsFilesLargerThanFiveMegabytes() {
		PrivateDocumentStorageService storage = new PrivateDocumentStorageService(
				temporaryDirectory.toString(), mock(StoredResidentDocumentRepository.class));
		byte[] large = new byte[(int) PrivateDocumentStorageService.MAX_FILE_SIZE + 1];
		MockMultipartFile upload = new MockMultipartFile("file", "photo.png", "image/png", large);

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class,
				() -> storage.store(upload, ResidentDocumentType.PHOTO));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
	}

	@Test
	void loadsExistingLegacyFileFromPrivateDirectory() throws Exception {
		StoredResidentDocumentRepository repository = mock(StoredResidentDocumentRepository.class);
		PrivateDocumentStorageService storage =
				new PrivateDocumentStorageService(temporaryDirectory.toString(), repository);
		String key = "123e4567-e89b-12d3-a456-426614174000.jpg";
		byte[] imageBytes = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
		Files.write(temporaryDirectory.resolve(key), imageBytes);

		ResidentDocument document = storage.load(key);

		assertArrayEquals(imageBytes, document.content());
		assertEquals("image/jpeg", document.mediaType().toString());
		verify(repository).save(any(StoredResidentDocument.class));
	}

	@Test
	void reportsLegacyFileMissingFromServerAsGone() {
		PrivateDocumentStorageService storage = new PrivateDocumentStorageService(
				temporaryDirectory.toString(), mock(StoredResidentDocumentRepository.class));
		String key = "123e4567-e89b-12d3-a456-426614174000.jpg";

		ResponseStatusException exception = assertThrows(
				ResponseStatusException.class, () -> storage.load(key));

		assertEquals(HttpStatus.GONE, exception.getStatusCode());
	}
}
