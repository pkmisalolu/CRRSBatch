package com.abcbs.crrs;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableBatchProcessing
public class CrrsBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(CrrsBatchApplication.class, args);
	}

}
