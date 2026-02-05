package com.abcbs.crrs.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class RetryConfig {

	private static final Logger logger = LogManager.getLogger(RetryConfig.class);
	
	@Bean
	public RetryTemplate retryTemplate() {
		logger.info("Entered into RetryTemplate");
	    RetryTemplate retryTemplate = new RetryTemplate();
	    SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
	    retryPolicy.setMaxAttempts(3);  // Retry 3 times

	    retryTemplate.setRetryPolicy(retryPolicy);
	    return retryTemplate;
	}
}
