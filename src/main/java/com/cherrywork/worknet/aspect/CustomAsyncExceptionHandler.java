package com.cherrywork.worknet.aspect;

import java.lang.reflect.Method;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

	@Override
	public void handleUncaughtException(Throwable throwable, Method method, Object... obj) {

		log.debug("Exception message - " + throwable.getMessage());
		log.debug("Method name - " + method.getName());
		for (Object param : obj) {
			log.debug("Parameter value - " + param);
		}
	}

}