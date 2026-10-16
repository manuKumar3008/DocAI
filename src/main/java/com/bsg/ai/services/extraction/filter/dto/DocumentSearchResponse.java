package com.bsg.ai.services.extraction.filter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentSearchResponse {

    @JsonProperty("resultList")
    private List<DocumentData> resultlist;
    private int resultSize;
    private boolean truncated;

    @Data
    public static class DocumentData {
        private List<String> locks = new ArrayList<>();
        private String subarchive;
        private String id;
        private int pageCount;
        private long size;
        private String mimeType;
        private String creationDate;
        private int status;
        private String formId;
        private int ftStatus;
        private int deleteState;
        private DocIndex docIndex;
        private Properties properties;
    }

    @Data
    public static class DocIndex {
        private String program;
        private String client;
        private String company;
        private String empNo;
        private String docType;
        private String subject;
        private String docDate;
        private int arcDocSubType;
        private String hash;
        private String fileName;
        private String modifiedDate;
        private int docPages;
    }

    @Data
    public static class Properties {
        private String arcServer;
        private String arcNr;
        private String lastUser;
        private String lockType;
        private Translations translations;
        private String arcDocType;
        private int arcDocSubType;
        private boolean pdfConvertable;
        private boolean previewable;
        private String filenameSuffix;
        private String fileContentType;
        private boolean viewDirect;
        private boolean isSplitable;
        private boolean isMergeable;
        private boolean isDeleteable;
        private boolean isRearchiveable;
        private boolean allowHistoryLog;
        private boolean isOfficeConvertable;
        private boolean isDownloadable;
        private boolean isAllowEncryptDocuments;
        private boolean isAllowCreateEncryptedLink;
        private boolean isAnnotableable;
    }

    @Data
    public static class Translations {
        private String subject;
        private String longName;
    }
}
