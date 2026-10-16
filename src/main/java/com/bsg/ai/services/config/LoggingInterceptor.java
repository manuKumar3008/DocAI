package com.bsg.ai.services.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class LoggingInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        log.trace("=== [Request] ===");
        log.trace("URI     : " + request.getURI());
        log.trace("Method  : " + request.getMethod());
        log.trace("Headers : " + request.getHeaders());
        log.trace("Body    : " + new String(body, StandardCharsets.UTF_8));

        ClientHttpResponse response = execution.execute(request, body);

        log.trace("=== [Response] ===");
        log.trace("Status code  : " + response.getStatusCode());
        log.trace("Status text  : " + response.getStatusText());
        log.trace("Headers      : " + response.getHeaders());
        // Note: Don't read body here unless you buffer it first

        return response;
    }
}

