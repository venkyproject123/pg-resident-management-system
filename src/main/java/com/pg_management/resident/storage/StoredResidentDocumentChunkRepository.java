package com.pg_management.resident.storage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredResidentDocumentChunkRepository extends JpaRepository<StoredResidentDocumentChunk, Long> {

	List<StoredResidentDocumentChunk> findAllByStorageKeyOrderByChunkIndex(String storageKey);

	void deleteAllByStorageKey(String storageKey);
}
