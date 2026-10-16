package com.bsg.ai.services.extraction.classification.pojo;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class OCROutput {

    private List<Map<String, Object>> classifications;
    private Map<String, List<Map<String, Object>>> properties;
    private List<Map<String, Object>> barcodes;
    private String full;
    private Map<String, ExtractedField> requiredFieldMapping;

    public List<Map<String, Object>> getClassifications() {
        return classifications;
    }

    public void setClassifications(List<Map<String, Object>> classifications) {
        this.classifications = classifications;
    }

    public Map<String, List<Map<String, Object>>> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, List<Map<String, Object>>> properties) {
        this.properties = properties;
    }

    public String getFull() {
        return full;
    }

    public void setFull(String full) {
        this.full = full;
    }

    public List<Map<String, Object>> getBarcodes() {
        return barcodes;
    }

    public void setBarcodes(List<Map<String, Object>> barcodes) {
        this.barcodes = barcodes;
    }

    public Map<String, ExtractedField> getRequiredFieldMapping() {
        return requiredFieldMapping;
    }

    public void setRequiredFieldMapping(Map<String, ExtractedField> requiredFieldMapping) {
        this.requiredFieldMapping = requiredFieldMapping;
    }

    @Override
    public int hashCode() {
        return Objects.hash(barcodes, classifications, full, properties, requiredFieldMapping);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OCROutput other = (OCROutput) obj;
        return Objects.equals(barcodes, other.barcodes)
                && Objects.equals(classifications, other.classifications)
                && Objects.equals(full, other.full)
                && Objects.equals(properties, other.properties)
                && Objects.equals(requiredFieldMapping, other.requiredFieldMapping);
    }

    @Override
    public String toString() {
        return "OCROutput [classifications=" + classifications
                + ", properties=" + properties
                + ", barcodes=" + barcodes
                + ", full=" + full
                + ", requiredFieldMapping=" + requiredFieldMapping
                + "]";
    }
}