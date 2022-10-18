package com.cherrywork.worknet.aspect;

import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class SpringAsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
    	  ThreadPoolTaskExecutor executor=	 new ThreadPoolTaskExecutor();
    	executor.setCorePoolSize(8);
        executor.setMaxPoolSize(30);
        executor.setQueueCapacity(Integer.MAX_VALUE);
        executor.setThreadNamePrefix("Thread-");
        executor.initialize();
    	
        return executor;
    }
    
    
    
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }
}