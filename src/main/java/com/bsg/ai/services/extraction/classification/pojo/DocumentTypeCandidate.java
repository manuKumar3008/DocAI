package com.bsg.ai.services.extraction.classification.pojo;

import java.util.List;

public class DocumentTypeCandidate {
    private String id;
    private String name;
    private String description;
    private List<String> mandatoryFields;

    public DocumentTypeCandidate() {
    }

    public DocumentTypeCandidate(String id, String name, String description, List<String> mandatoryFields) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.mandatoryFields = mandatoryFields;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getMandatoryFields() { return mandatoryFields; }
    public void setMandatoryFields(List<String> mandatoryFields) { this.mandatoryFields = mandatoryFields; }
}