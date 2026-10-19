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
		if (hasColumn("owner_accounts", "mobile_number")) {
			List<String> uniqueIndexes = jdbcTemplate.queryForList("""
					SELECT DISTINCT INDEX_NAME
					FROM information_schema.statistics
					WHERE TABLE_SCHEMA = DATABASE()
					  AND TABLE_NAME = 'owner_accounts'
					  AND COLUMN_NAME = 'mobile_number'
					  AND NON_UNIQUE = 0
					  AND INDEX_NAME <> 'PRIMARY'
					""", String.class);
			for (String index : uniqueIndexes) {
				jdbcTemplate.execute("ALTER TABLE owner_accounts DROP INDEX `" + index.replace("`", "``") + "`");
			}
			jdbcTemplate.execute("ALTER TABLE owner_accounts MODIFY COLUMN mobile_number VARCHAR(16) NULL");
		}
		if (hasColumn("residents", "owner_id")) {
			jdbcTemplate.update("""
					UPDATE residents resident
					JOIN (SELECT id FROM owner_accounts ORDER BY id LIMIT 1) first_owner
					  ON resident.owner_id IS NULL
					SET resident.owner_id = first_owner.id
					""");
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
