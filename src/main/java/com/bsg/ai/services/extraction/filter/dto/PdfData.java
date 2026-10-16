package com.bsg.ai.services.extraction.filter.dto;

import java.awt.image.BufferedImage;
import java.util.List;

public class PdfData {
    private String content;
    private List<BufferedImage> images;
    private int pages;

    public PdfData(String content, List<BufferedImage> images, int pages) {
        this.content = content;
        this.images = images;
        this.pages = pages;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<BufferedImage> getImages() {
        return images;
    }

    public void setImages(List<BufferedImage> images) {
        this.images = images;
    }

    @Override
    public String toString() {
        return "Text is: " + this.getContent() + " and image count is :" + ((this.getImages() != null && !this.getImages().isEmpty()) ? this.getImages().size() : 0);
    }
}
