package com.pg_management.security;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OwnerTenantSchemaMigration implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	public OwnerTenantSchemaMigration(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		migrateLegacyOwnerContactColumns();
		if (hasColumn("residents", "owner_id")) {
			jdbcTemplate.update("""
					UPDATE residents resident
					JOIN (SELECT id FROM owner_accounts ORDER BY id LIMIT 1) first_owner
					  ON resident.owner_id IS NULL
					SET resident.owner_id = first_owner.id
					""");
		}
		if (hasColumn("owner_accounts", "pg_name")) {
			jdbcTemplate.update("UPDATE owner_accounts SET pg_name = 'PG' WHERE pg_name IS NULL OR pg_name = ''");
		}
		migrateResidentDocumentContentColumn();
	}

	private void migrateLegacyOwnerContactColumns() {
		boolean hasMobileNumber = hasColumn("owner_accounts", "mobile_number");
		boolean hasLegacyNumber = hasColumn("owner_accounts", "whatsapp_number");
		if (!hasMobileNumber && hasLegacyNumber) {
			jdbcTemplate.execute("ALTER TABLE owner_accounts ADD COLUMN mobile_number VARCHAR(20) NULL");
			hasMobileNumber = true;
		}
		if (hasMobileNumber && hasLegacyNumber) {
			jdbcTemplate.update("""
					UPDATE owner_accounts
					SET mobile_number = whatsapp_number
					WHERE mobile_number IS NULL OR mobile_number = ''
					""");
		}
		if (hasLegacyNumber) {
			jdbcTemplate.execute("ALTER TABLE owner_accounts DROP COLUMN whatsapp_number");
		}
		if (hasColumn("owner_accounts", "whatsapp_verified")) {
			jdbcTemplate.execute("ALTER TABLE owner_accounts DROP COLUMN whatsapp_verified");
		}
	}

	private void migrateResidentDocumentContentColumn() {
		if (!hasColumn("resident_document_files", "content")) {
			return;
		}

		String dataType = jdbcTemplate.queryForObject("""
				SELECT DATA_TYPE
				FROM information_schema.columns
				WHERE TABLE_SCHEMA = DATABASE()
				  AND TABLE_NAME = ?
				  AND COLUMN_NAME = ?
				""", String.class, "resident_document_files", "content");
		if (!"mediumblob".equalsIgnoreCase(dataType) && !"longblob".equalsIgnoreCase(dataType)) {
			jdbcTemplate.execute(
					"ALTER TABLE resident_document_files MODIFY COLUMN content MEDIUMBLOB NOT NULL");
		}
	}

	private boolean hasColumn(String table, String column) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT COLUMN_NAME
				FROM information_schema.columns
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
				""", table, column);
		return !rows.isEmpty();
	}
}
