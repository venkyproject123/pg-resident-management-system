package com.pg_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PgManagementApplication {

	public static void main(String[] args) {

        SpringApplication.run(PgManagementApplication.class, args);
	}

}
