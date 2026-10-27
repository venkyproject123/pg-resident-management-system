package com.pg_management.resident.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "resident_document_files")
public class StoredResidentDocument {

	@Id
	@Column(name = "storage_key", length = 50)
	private String storageKey;

	@Lob
	@Column(nullable = false)
	private byte[] content;

	@Column(name = "media_type", nullable = false, length = 100)
	private String mediaType;

	protected StoredResidentDocument() {
	}

	public StoredResidentDocument(String storageKey, byte[] content, String mediaType) {
		this.storageKey = storageKey;
		this.content = content;
		this.mediaType = mediaType;
	}

	public byte[] getContent() {
		return content;
	}

	public String getMediaType() {
		return mediaType;
	}
}
