package com.pg_management.resident.storage;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PrivateDocumentStorageService {

	public static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

	private static final Pattern STORAGE_KEY_PATTERN =
			Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|pdf)");
	private static final int STORAGE_CHUNK_SIZE = 16 * 1024;

	private final Path storageDirectory;
	private final StoredResidentDocumentRepository documentRepository;
	private final StoredResidentDocumentChunkRepository chunkRepository;

	public PrivateDocumentStorageService(
			@Value("${app.upload.directory}") String directory,
			StoredResidentDocumentRepository documentRepository,
			StoredResidentDocumentChunkRepository chunkRepository) {
		this.documentRepository = documentRepository;
		this.chunkRepository = chunkRepository;
		storageDirectory = Path.of(directory).toAbsolutePath().normalize();
		try {
			Files.createDirectories(storageDirectory);
			setPosixPermissions(
					storageDirectory,
					PosixFilePermissions.fromString("rwx------"));
		} catch (IOException exception) {
			throw new IllegalStateException("Could not initialize private document storage", exception);
		}
	}

	@Transactional
	public String store(MultipartFile upload, ResidentDocumentType documentType) {
		if (upload == null || upload.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty file");
		}
		if (upload.getSize() > MAX_FILE_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Files must be 5 MB or smaller");
		}

		byte[] content;
		try {
			content = upload.getBytes();
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read uploaded file");
		}
		if (content.length == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty file");
		}
		if (content.length > MAX_FILE_SIZE) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Files must be 5 MB or smaller");
		}

		String extension = detectExtension(content, documentType);
		String storageKey = UUID.randomUUID() + "." + extension;
		MediaType mediaType = mediaTypeFor(storageKey);
		persistDocument(storageKey, content, mediaType);
		return storageKey;
	}

	public ResidentDocument load(String storageKey) {
		validateStorageKey(storageKey);
		var storedDocument = documentRepository.findById(storageKey);
		if (storedDocument.isEmpty()) {
			return loadLegacyFile(storageKey);
		}
		StoredResidentDocument document = storedDocument.get();
		byte[] content = document.getContent();
		if (content.length == 0) {
			content = loadChunks(storageKey);
		}
		return new ResidentDocument(content, MediaType.parseMediaType(document.getMediaType()));
	}

	private byte[] loadChunks(String storageKey) {
		List<StoredResidentDocumentChunk> chunks = chunkRepository.findAllByStorageKeyOrderByChunkIndex(storageKey);
		ByteArrayOutputStream content = new ByteArrayOutputStream();
		for (StoredResidentDocumentChunk chunk : chunks) {
			content.writeBytes(chunk.getContent());
		}
		return content.toByteArray();
	}

	private ResidentDocument loadLegacyFile(String storageKey) {
		Path path = resolveStoragePath(storageKey);
		try {
			byte[] content = Files.readAllBytes(path);
			MediaType mediaType = mediaTypeFor(storageKey);
			persistDocument(storageKey, content, mediaType);
			return new ResidentDocument(content, mediaType);
		} catch (java.nio.file.NoSuchFileException exception) {
			throw new ResponseStatusException(
					HttpStatus.GONE, "The uploaded file is no longer available on this server. Please upload it again.");
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read resident document");
		}
	}

	@Transactional
	public void delete(String storageKey) {
		Path path = resolveStoragePath(storageKey);
		chunkRepository.deleteAllByStorageKey(storageKey);
		documentRepository.deleteById(storageKey);
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not remove replaced document");
		}
	}

	private void persistDocument(String storageKey, byte[] content, MediaType mediaType) {
		for (int offset = 0, index = 0; offset < content.length; offset += STORAGE_CHUNK_SIZE, index++) {
			int end = Math.min(content.length, offset + STORAGE_CHUNK_SIZE);
			chunkRepository.saveAndFlush(new StoredResidentDocumentChunk(
					storageKey, index, java.util.Arrays.copyOfRange(content, offset, end)));
		}
		documentRepository.saveAndFlush(
				new StoredResidentDocument(storageKey, new byte[0], mediaType.toString()));
	}

	private Path resolveStoragePath(String storageKey) {
		validateStorageKey(storageKey);
		Path path = storageDirectory.resolve(storageKey).normalize();
		if (!path.startsWith(storageDirectory)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident document not found");
		}
		return path;
	}

	private void validateStorageKey(String storageKey) {
		if (storageKey == null || !STORAGE_KEY_PATTERN.matcher(storageKey).matches()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident document not found");
		}
	}

	private String detectExtension(byte[] content, ResidentDocumentType documentType) {
		if (isJpeg(content)) {
			return "jpg";
		}
		if (isPng(content)) {
			return "png";
		}
		if (documentType == ResidentDocumentType.IDENTITY_PROOF && isPdf(content)) {
			return "pdf";
		}
		throw new ResponseStatusException(
				HttpStatus.UNSUPPORTED_MEDIA_TYPE,
				documentType == ResidentDocumentType.PHOTO
						? "Photo must be a JPEG or PNG image"
						: "Identity proof must be a JPEG, PNG, or PDF file");
	}

	private boolean isJpeg(byte[] content) {
		return content.length >= 3
				&& (content[0] & 0xff) == 0xff
				&& (content[1] & 0xff) == 0xd8
				&& (content[2] & 0xff) == 0xff;
	}

	private boolean isPng(byte[] content) {
		return content.length >= 8
				&& (content[0] & 0xff) == 0x89
				&& content[1] == 0x50
				&& content[2] == 0x4e
				&& content[3] == 0x47
				&& content[4] == 0x0d
				&& content[5] == 0x0a
				&& content[6] == 0x1a
				&& content[7] == 0x0a;
	}

	private boolean isPdf(byte[] content) {
		return content.length >= 5
				&& content[0] == 0x25
				&& content[1] == 0x50
				&& content[2] == 0x44
				&& content[3] == 0x46
				&& content[4] == 0x2d;
	}

	private MediaType mediaTypeFor(String storageKey) {
		if (storageKey.endsWith(".jpg")) {
			return MediaType.IMAGE_JPEG;
		}
		if (storageKey.endsWith(".png")) {
			return MediaType.IMAGE_PNG;
		}
		return MediaType.APPLICATION_PDF;
	}

	private void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
		try {
			Files.setPosixFilePermissions(path, permissions);
		} catch (UnsupportedOperationException exception) {
			// Windows uses the storage directory's inherited user-profile ACLs.
		}
	}

}
