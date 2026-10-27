package com.pg_management.resident.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredResidentDocumentRepository extends JpaRepository<StoredResidentDocument, String> {
}
