package com.bsg.ai.services.extraction.classification.pojo;

public class ExtractedField {

    private String value;
    private double confidence;

    public ExtractedField() {
    }

    public ExtractedField(String value, double confidence) {
        this.value = value;
        this.confidence = confidence;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}