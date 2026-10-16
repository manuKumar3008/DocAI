package com.bsg.ai.services.config;

import com.bsg.ai.services.extraction.filter.dto.RestAPIErrorVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RestControllerAdvice
public class GlobalRestExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        log.error("MethodArgumentNotValid exception occurred.", ex);
        final List<String> errors = new ArrayList<>();
        for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
            var errorMsg = error.getDefaultMessage();
            errorMsg = (errorMsg == null) || errorMsg.endsWith(".") ? errorMsg : errorMsg + ".";
            var fieldName =
                    error.getField().contains(".") ? error.getField().substring(error.getField().lastIndexOf('.') + 1)
                            : error.getField();
            errors.add(fieldName + " " + errorMsg);
        }
        for (final ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add(error.getObjectName() + ": " + error.getDefaultMessage());
        }
        log.error("Customized error message is: {}.", errors);
        var restApiErrorVO = new RestAPIErrorVO(HttpStatus.BAD_REQUEST, StringUtils.join(errors, '\n'));
        return handleExceptionInternal(ex, restApiErrorVO, headers, restApiErrorVO.status(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        log.error("MissingServletRequestParameter exception occurred.", ex);
        var restApiErrorVO = new RestAPIErrorVO(HttpStatus.BAD_REQUEST, ex.getMessage());
        return new ResponseEntity<>(restApiErrorVO, new HttpHeaders(), restApiErrorVO.status());
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        log.error("HttpRequestMethodNotSupported exception occurred.", ex);
        var restApiErrorVO = new RestAPIErrorVO(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
        return new ResponseEntity<>(restApiErrorVO, new HttpHeaders(), restApiErrorVO.status());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                     HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        log.error("HttpMediaTypeNotSupportedException exception occurred.", ex);
        var restApiErrorVO = new RestAPIErrorVO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        return new ResponseEntity<>(restApiErrorVO, new HttpHeaders(), restApiErrorVO.status());
    }

    @ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(value = {Exception.class})
    public @ResponseBody RestAPIErrorVO handleAllException(Exception ex, HttpServletRequest request) throws IOException {
        logExceptionWithDetails(ex.getMessage(), ex, request);
        return new RestAPIErrorVO(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private void logExceptionWithDetails(String message, Exception ex, HttpServletRequest request) {
        var requestUrl = request.getRequestURI();

        // Log headers
        var headers = new StringBuilder();
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            var name = headerNames.nextElement();
            var value = request.getHeader(name);
            headers.append(name).append(": ").append(value).append(", ");
        }

        // Log parameters
        var paramString = new StringBuilder();
        var parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            var name = parameterNames.nextElement();
            var value = request.getParameter(name);
            paramString.append(name).append(": ").append(value).append(", ");
        }

        var requestBody = "";
        if (request instanceof ContentCachingRequestWrapper) {
            var wrappedRequest = (ContentCachingRequestWrapper) request;
            var contentAsByteArray = wrappedRequest.getContentAsByteArray();
            if (contentAsByteArray != null && contentAsByteArray.length > 0) {
                requestBody = new String(contentAsByteArray, StandardCharsets.UTF_8);
            }
        }

        log.error("{} | URL: {} | Headers: {} | Request Params: {} | Request Body: {}",
                message, requestUrl, headers, paramString, requestBody, ex);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Void> handleIOException(IOException ex) {
        if (ex.getMessage().contains("Broken pipe")) {
            log.warn("Client disconnected before response could be sent.");
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        log.error("Unexpected IO error", ex);
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
