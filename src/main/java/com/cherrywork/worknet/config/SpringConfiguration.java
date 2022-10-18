package com.cherrywork.worknet.config;

import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@ComponentScan(basePackages = {"com.cherrywork"})
public class SpringConfiguration{

	@Bean
	public ThreadPoolTaskScheduler taskScheduler(TaskSchedulerBuilder builder) {
	    return builder.build();
	}
	
	@Bean
	public ObjectMapper mapper() {
		return new ObjectMapper();
	}
}
