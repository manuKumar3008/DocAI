package com.bsg.ai.services.extraction.filter.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SubjectsResponse {

    private List<String> documentTypes;
    private List<String> subjects;
    private List<DocumentTypeWiseSubjects> documentTypeWiseSubjects;

    @Data
    public static class DocumentTypeWiseSubjects {
        public String documentType;
        public List<SubjectKeyValue> subjects;
    }

    @Data
    public static class SubjectKeyValue {
        public String key;
        public String value;
    }
}
