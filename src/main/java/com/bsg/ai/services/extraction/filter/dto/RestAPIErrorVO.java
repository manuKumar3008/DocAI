package com.bsg.ai.services.extraction.filter.dto;

import org.springframework.http.HttpStatus;

public record RestAPIErrorVO(HttpStatus status, String message) {
}
