package com.lucho.tienda.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;

@Slf4j
public class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

    private static final String LOG_EXCEPTION_MSG = "Exception message - {}";
    private static final String LOG_METHOD_NAME = "Method name - {}";
    private static final String LOG_PARAM_VALUE = "Parameter value - {}";
    private static final String LOG_UNEXPECTED_ERROR = "Unexpected error occurred in async processing";

    @Override
    public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
        log.error(LOG_EXCEPTION_MSG, throwable.getMessage());
        log.error(LOG_METHOD_NAME, method.getName());
        for (Object param : params) {
            log.error(LOG_PARAM_VALUE, param);
        }
        log.error(LOG_UNEXPECTED_ERROR, throwable);
    }
}