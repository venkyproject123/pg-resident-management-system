package com.pg_management.security;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnerAccountRepository extends JpaRepository<OwnerAccount, Long> {

	Optional<OwnerAccount> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByMobileNumber(String mobileNumber);

	boolean existsByMobileNumberAndIdNot(String mobileNumber, Long id);

	boolean existsByRecoveryIdHash(String recoveryIdHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select owner from OwnerAccount owner where owner.recoveryIdHash = :recoveryIdHash")
	Optional<OwnerAccount> findByRecoveryIdHashForUpdate(@Param("recoveryIdHash") String recoveryIdHash);

	List<OwnerAccount> findAllByApprovedFalseAndRemovedFalseOrderByIdAsc();

	List<OwnerAccount> findAllByApprovedTrueAndRemovedFalseOrderByIdAsc();

	long countByApprovedTrueAndRemovedFalse();
}
