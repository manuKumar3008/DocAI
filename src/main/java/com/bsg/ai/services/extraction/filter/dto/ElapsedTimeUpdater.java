package com.bsg.ai.services.extraction.filter.dto;

@FunctionalInterface
public interface ElapsedTimeUpdater {
    void update(long elapsedTime);
}
