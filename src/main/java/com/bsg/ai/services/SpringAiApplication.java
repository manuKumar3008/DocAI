package com.bsg.ai.services;

import com.bsg.ai.services.extraction.multiDocQa.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableCaching
@EnableConfigurationProperties(RagProperties.class)
public class SpringAiApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder.sources(SpringAiApplication.class);
	}

	public static void main(String[] args) {
		System.setProperty("jna.encoding", "UTF8");
		SpringApplication.run(SpringAiApplication.class, args);
	}

}
