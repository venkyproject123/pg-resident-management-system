package com.pg_management.security;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerAccountRepository extends JpaRepository<OwnerAccount, Long> {

	Optional<OwnerAccount> findByUsername(String username);

	Optional<OwnerAccount> findByWhatsappNumber(String whatsappNumber);

	Optional<OwnerAccount> findByUsernameAndWhatsappNumber(String username, String whatsappNumber);
}
