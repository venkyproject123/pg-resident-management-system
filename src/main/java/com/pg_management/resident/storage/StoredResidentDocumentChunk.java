package com.pg_management.resident.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "resident_document_chunks",
		uniqueConstraints = @UniqueConstraint(columnNames = {"storage_key", "chunk_index"}))
public class StoredResidentDocumentChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "storage_key", nullable = false, length = 50)
	private String storageKey;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	@Column(nullable = false, columnDefinition = "MEDIUMBLOB")
	private byte[] content;

	protected StoredResidentDocumentChunk() {
	}

	public StoredResidentDocumentChunk(String storageKey, int chunkIndex, byte[] content) {
		this.storageKey = storageKey;
		this.chunkIndex = chunkIndex;
		this.content = content;
	}

	public byte[] getContent() {
		return content;
	}
}
