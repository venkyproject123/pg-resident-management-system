package com.pg_management.security;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminCredentialRepository extends JpaRepository<AdminCredential, String> {
}
