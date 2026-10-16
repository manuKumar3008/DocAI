package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos;

public class Cell {
    private String text;
    private int rowIndex;
    private int colIndex;
    private int rowSpan;
    private int colSpan;
    private float x;
    private float y;
    private float width;
    private float height;

    public Cell() {
        this("", 0, 0, 1, 1, 0f, 0f, 0f, 0f);
    }

    public Cell(String text, int rowIndex, int colIndex, int rowSpan, int colSpan,
                float x, float y, float width, float height) {
        this.text = text;
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.rowSpan = Math.max(1, rowSpan);
        this.colSpan = Math.max(1, colSpan);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    public int getRowSpan() {
        return rowSpan;
    }

    public void setRowSpan(int rowSpan) {
        this.rowSpan = Math.max(1, rowSpan);
    }

    public int getColSpan() {
        return colSpan;
    }

    public void setColSpan(int colSpan) {
        this.colSpan = Math.max(1, colSpan);
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    public float getWidth() {
        return width;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float getHeight() {
        return height;
    }

    public void setHeight(float height) {
        this.height = height;
    }
}
