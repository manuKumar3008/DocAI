package com.bsg.ai.services.extraction.filter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PersonSearchResponse {

    @JsonProperty("resultList")
    private List<PersonData> resultlist;
    private int resultSize;
    private boolean truncated;

    @Data
    public static class PersonData {
        private String id;
        private String client;
        private String company;
        private String empNo;
        private String firstName;
        private String surName;
        private String personType;
        private boolean deleteDataAfterLeaving;
        private int deleteState;
        private boolean forCompliance;
    }
}
