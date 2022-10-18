package com.cherrywork.worknet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAspectJAutoProxy
@ComponentScan(basePackages = { "com.cherrywork.worknet" })
public class WorknetApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorknetApplication.class, args);
	}

}
