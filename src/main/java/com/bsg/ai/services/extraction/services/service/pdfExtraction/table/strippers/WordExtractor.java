package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Word;
import lombok.Getter;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.util.ArrayList;
import java.util.List;

@Getter
public class WordExtractor extends PDFTextStripper {
    private final List<Word> words = new ArrayList<>();
    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        TextPosition first = positions.get(0);
        float minX = first.getXDirAdj(), minY = first.getYDirAdj();
        float maxX = positions.get(positions.size()-1).getXDirAdj() + positions.get(positions.size()-1).getWidthDirAdj();
        float maxY = minY + first.getHeightDir();
        words.add(new Word(text.trim(), minX, minY, maxX - minX, maxY - minY));
    }
}
