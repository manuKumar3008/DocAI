package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Word;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers.WordExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class HybridBorderlessTableExtractor {

    private static final float ROW_GROUP_TOLERANCE = 3.0f;
    private static final float TABLE_REGION_PADDING = 1.5f;
    private static final float DPI = 144f;

    public List<DetectedTable> extractPageTables(PDDocument document,
                                                 int pageIndex,
                                                 List<DetectedTable> borderedTables,
                                                 int startingTableIndex) throws IOException {
        List<Rectangle2D.Float> excludedRegions = resolveTableRegions(borderedTables);

        List<Word> words = collectWords(document, pageIndex, excludedRegions);
        if (words.isEmpty()) {
            return Collections.emptyList();
        }
        float dynamicGutterThreshold = calculateDynamicGap(words);

        List<List<Word>> lines = groupWordsByLine(words);
        List<List<List<Word>>> regions = splitIntoHorizontalRegions(lines);

        List<DetectedTable> tables = new ArrayList<>();
        int tableIndex = startingTableIndex;
        for (List<List<Word>> region : regions) {
            List<Word> regionWords = region.stream().flatMap(List::stream).collect(Collectors.toList());
            if (regionWords.isEmpty()) {
                continue;
            }

            float minX = (float) regionWords.stream().mapToDouble(Word::x).min().orElse(0f);
            float maxX = (float) regionWords.stream().mapToDouble(word -> word.x() + word.width()).max().orElse(minX);
            List<float[]> columnBands = detectHybridBands(region, minX, maxX - minX, dynamicGutterThreshold);
            if (columnBands.size() < 2) {
                continue;
            }

            DetectedTable table = tryExtractTable(region, columnBands, pageIndex, tableIndex);
            if (table != null && !table.getRows().isEmpty()) {
                tables.add(table);
                tableIndex++;
            }
        }
        return tables;
    }

    /**
     * Converts page to grayscale and identifies vertical "ink" columns.
     * Pure white columns (gutters) will be 'false'.
     */
    private static boolean[] getVerticalInkMap(PDDocument document, int pageNum) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(pageNum, HybridBorderlessTableExtractor.DPI);
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[] hasInk = new boolean[w];

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                Color color = new Color(image.getRGB(x, y));
                // Convert to Grayscale value
                int luminance = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());

                // If the pixel is dark enough, it's considered "ink"
                if (luminance < 230) {
                    hasInk[x] = true;
                    break;
                }
            }
        }
        return hasInk;
    }

    private List<Word> collectWords(PDDocument document,
                                    int pageIndex,
                                    List<Rectangle2D.Float> excludedRegions) throws IOException {

        WordExtractor extractor = new WordExtractor();
        extractor.setStartPage(pageIndex + 1);
        extractor.setEndPage(pageIndex + 1);

        extractor.getText(document); // triggers writeString()

        List<Word> extractedWords = extractor.getWords();
        if (extractedWords.isEmpty()) {
            return Collections.emptyList();
        }

        List<Word> filteredWords = new ArrayList<>();
        for (Word word : extractedWords) {
            if (!intersectsAnyExcludedRegion(word, excludedRegions)) {
                filteredWords.add(word);
            }
        }

        return filteredWords;
    }

    private boolean intersectsAnyExcludedRegion(Word word, List<Rectangle2D.Float> regions) {
        if (regions == null || regions.isEmpty()) {
            return false;
        }
        float wordLeft = word.x();
        float wordRight = word.x() + word.width();
        float wordTop = word.y();
        float wordBottom = word.y() + word.height();
        for (Rectangle2D.Float region : regions) {
            float overlapX = Math.min(wordRight, region.x + region.width) - Math.max(wordLeft, region.x);
            float overlapY = Math.min(wordBottom, region.y + region.height) - Math.max(wordTop, region.y);
            if (overlapX > 0f && overlapY > 0f) {
                return true;
            }
        }
        return false;
    }

    private List<List<Word>> groupWordsByLine(List<Word> words) {
        words.sort(Comparator.comparing(Word::y).thenComparing(Word::x));
        List<List<Word>> lines = new ArrayList<>();
        if (words.isEmpty()) {
            return lines;
        }

        List<Word> currentLine = new ArrayList<>();
        currentLine.add(words.get(0));
        for (int i = 1; i < words.size(); i++) {
            if (Math.abs(words.get(i).y() - currentLine.get(0).y()) < ROW_GROUP_TOLERANCE) {
                currentLine.add(words.get(i));
            } else {
                lines.add(currentLine);
                currentLine = new ArrayList<>();
                currentLine.add(words.get(i));
            }
        }
        lines.add(currentLine);
        return lines;
    }

    private List<List<List<Word>>> splitIntoHorizontalRegions(List<List<Word>> lines) {
        List<List<List<Word>>> regions = new ArrayList<>();
        if (lines.isEmpty()) {
            return regions;
        }

        List<Float> gaps = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            float gap = lines.get(i).get(0).y() - (lines.get(i - 1).get(0).y() + lines.get(i - 1).get(0).height());
            gaps.add(Math.max(0f, gap));
        }
        float avgGap = (float) gaps.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
        float dynamicThreshold = Math.max(avgGap * 1.5f, 12.0f);

        List<List<Word>> currentRegion = new ArrayList<>();
        currentRegion.add(lines.get(0));

        for (int i = 1; i < lines.size(); i++) {
            float gap = lines.get(i).get(0).y() - (lines.get(i - 1).get(0).y() + lines.get(i - 1).get(0).height());
            if (gap > dynamicThreshold) {
                regions.add(currentRegion);
                currentRegion = new ArrayList<>();
            }
            currentRegion.add(lines.get(i));
        }
        regions.add(currentRegion);
        return regions;
    }

//    private List<List<List<Word>>> splitIntoHorizontalRegions(List<List<Word>> lines) {
//        List<List<List<Word>>> regions = new ArrayList<>();
//        if (lines.isEmpty()) return regions;
//
//        List<Float> gaps = new ArrayList<>();
//
//        // Helper functions
//        java.util.function.Function<List<Word>, Float> top =
//                line -> line.stream().map(Word::y).min(Float::compare).orElse(0f);
//
//        java.util.function.Function<List<Word>, Float> bottom =
//                line -> line.stream()
//                        .map(w -> w.y() + w.height())
//                        .max(Float::compare)
//                        .orElse(0f);
//
//        // Step 1: compute normalized gaps
//        for (int i = 1; i < lines.size(); i++) {
//            float prevBottom = bottom.apply(lines.get(i - 1));
//            float currTop = top.apply(lines.get(i));
//
//            float prevHeight = prevBottom - top.apply(lines.get(i - 1));
//            float gap = Math.max(0f, currTop - prevBottom);
//
//            float normalizedGap = prevHeight > 0 ? gap / prevHeight : gap;
//            gaps.add(normalizedGap);
//        }
//
//        // Step 2: sort for median/IQR
//        Collections.sort(gaps);
//        float threshold = 0;
//        if (!gaps.isEmpty()) {
//            float q1 = gaps.get(gaps.size() / 4);
//            float q3 = gaps.get(3 * gaps.size() / 4);
//            float iqr = q3 - q1;
//
//            threshold = q3 + 1.5f * iqr;
//        }
//
//        // Fallback safety
//        threshold = Math.max(threshold, 1.0f);
//
//        // Step 3: grouping
//        List<List<Word>> currentRegion = new ArrayList<>();
//        currentRegion.add(lines.get(0));
//
//        for (int i = 1; i < lines.size(); i++) {
//            float prevBottom = bottom.apply(lines.get(i - 1));
//            float currTop = top.apply(lines.get(i));
//
//            float prevHeight = prevBottom - top.apply(lines.get(i - 1));
//            float gap = Math.max(0f, currTop - prevBottom);
//
//            float normalizedGap = prevHeight > 0 ? gap / prevHeight : gap;
//
//            if (normalizedGap > threshold) {
//                regions.add(currentRegion);
//                currentRegion = new ArrayList<>();
//            }
//
//            currentRegion.add(lines.get(i));
//        }
//
//        regions.add(currentRegion);
//        return regions;
//    }

    private float calculateDynamicGap(List<Word> words) {
        double avgCharWidth = words.stream()
                .filter(word -> StringUtils.isNotBlank(word.text()))
                .mapToDouble(word -> word.width() / word.text().length())
                .average()
                .orElse(6.0);
        return (float) (avgCharWidth * 1.6);
    }

    private List<float[]> detectHybridBands(List<List<Word>> lines, float minX, float width, float threshold) {
        if (width <= 0f) {
            return Collections.emptyList();
        }
        int startX = (int) minX;
        int endX = (int) (minX + width);
        int bandStart = startX;
        List<float[]> bands = new ArrayList<>();

        for (int x = startX; x < endX; x++) {
            if (!isWordAt(lines, x)) {
                int gapWidth = 0;
                int tempX = x;
                while (tempX < endX && !isWordAt(lines, tempX)) {
                    gapWidth++;
                    tempX++;
                }
                if (gapWidth >= threshold) {
                    bands.add(new float[]{bandStart, x});
                    bandStart = tempX;
                    x = tempX;
                }
            }
        }
        bands.add(new float[]{bandStart, minX + width});
        return bands;
    }
//private List<float[]> detectHybridBands(List<List<Word>> lines,
//                                        float minX,
//                                        float width,
//                                        float scoreThreshold) {
//
//    if (width <= 0f) return Collections.emptyList();
//
//    int startX = (int) minX;
//    int endX = (int) (minX + width);
//
//    List<Float> separatorXs = new ArrayList<>();
//
//    // Step 1: compute average word gap (baseline)
//    List<Float> allGaps = new ArrayList<>();
//
//    for (List<Word> line : lines) {
//        line.sort(Comparator.comparing(Word::x));
//        for (int i = 1; i < line.size(); i++) {
//            float gap = line.get(i).x() - (line.get(i - 1).x() + line.get(i - 1).width());
//            if (gap > 0) allGaps.add(gap);
//        }
//    }
//
//    float avgGap = allGaps.isEmpty() ? 1f :
//            (float) allGaps.stream().mapToDouble(Float::doubleValue).average().orElse(1.0);
//
//    // Step 2: evaluate each word pair
//    for (List<Word> line : lines) {
//        line.sort(Comparator.comparing(Word::x));
//
//        for (int i = 1; i < line.size(); i++) {
//            Word left = line.get(i - 1);
//            Word right = line.get(i);
//
//            float gap = right.x() - (left.x() + left.width());
//            if (gap <= 0) continue;
//
//            float separatorX = left.x() + left.width() + gap / 2;
//
//            // Step 3: scoring
//            float score = computeGapScore(lines, separatorX, gap, avgGap);
//            float leftWidth = left.width();
//            float rightWidth = right.width();
//
//            if (rightWidth > leftWidth * 5) {
//                score -= 25f;
//            }
//            if (score >= scoreThreshold) {
//                separatorXs.add(separatorX);
//            }
//        }
//    }
//
//    // Step 4: merge close separators
//    Collections.sort(separatorXs);
//    List<Float> mergedSeparators = mergeCloseSeparators(separatorXs, 10f);
//
//    // Step 5: build bands
//    List<float[]> bands = new ArrayList<>();
//    float bandStart = minX;
//
//    for (float sep : mergedSeparators) {
//        bands.add(new float[]{bandStart, sep});
//        bandStart = sep;
//    }
//
//    bands.add(new float[]{bandStart, minX + width});
//
//    return bands;
//}

    private float computeGapScore(List<List<Word>> lines,
                                  float separatorX,
                                  float gap,
                                  float avgGap) {

        float score = 0f;

        // 1. Gap size score (0–40)
        float gapRatio = gap / avgGap;
        score += Math.min(gapRatio * 20f, 40f);

        // 2. Vertical alignment consistency (0–30)
        int alignedLines = 0;

        for (List<Word> line : lines) {
            for (int i = 1; i < line.size(); i++) {
                Word left = line.get(i - 1);
                Word right = line.get(i);

                float mid = left.x() + left.width() +
                        (right.x() - (left.x() + left.width())) / 2;

                if (Math.abs(mid - separatorX) < 5f) {
                    alignedLines++;
                    break;
                }
            }
        }

        float alignmentScore = Math.min(alignedLines * 5f, 30f);
        score += alignmentScore;

        // 3. Gap consistency across lines (0–30)
        int similarGaps = 0;

        for (List<Word> line : lines) {
            for (int i = 1; i < line.size(); i++) {
                float g = line.get(i).x() - (line.get(i - 1).x() + line.get(i - 1).width());
                if (Math.abs(g - gap) < avgGap * 0.5f) {
                    similarGaps++;
                }
            }
        }

        float consistencyScore = Math.min(similarGaps * 3f, 30f);
        score += consistencyScore;

        return Math.min(score, 100f);
    }

    private List<Float> mergeCloseSeparators(List<Float> separators, float tolerance) {
        List<Float> merged = new ArrayList<>();

        for (float sep : separators) {
            if (merged.isEmpty() || Math.abs(merged.get(merged.size() - 1) - sep) > tolerance) {
                merged.add(sep);
            }
        }

        return merged;
    }

    private boolean isWordAt(List<List<Word>> lines, int x) {
        for (List<Word> line : lines) {
            for (Word word : line) {
                if (StringUtils.isNotBlank(word.text()) && x >= word.x() && x <= (word.x() + word.width())) {
                    return true;
                }
            }
        }
        return false;
    }

    private DetectedTable tryExtractTable(List<List<Word>> lines,
                                          List<float[]> bands,
                                          int pageIndex,
                                          int tableIndex) {
        DetectedTable table = new DetectedTable(pageIndex, tableIndex);
        int outputRowIndex = 0;

        for (List<Word> line : lines) {
            String[] row = new String[bands.size()];
            Arrays.fill(row, "");

            List<List<Word>> wordsByBand = new ArrayList<>();
            for (int i = 0; i < bands.size(); i++) {
                wordsByBand.add(new ArrayList<>());
            }

            for (Word word : line) {
                float centerX = word.x() + (word.width() / 2f);
                int matchedIndex = -1;
                for (int i = 0; i < bands.size(); i++) {
                    float bandStart = bands.get(i)[0];
                    float bandEnd = bands.get(i)[1];
                    if (centerX >= bandStart && centerX <= bandEnd) {
                        matchedIndex = i;
                        break;
                    }
                }
                if (matchedIndex >= 0) {
                    wordsByBand.get(matchedIndex).add(word);
                }
            }

            long nonEmptyColumns = 0;
            for (int i = 0; i < bands.size(); i++) {
                String value = wordsByBand.get(i).stream()
                        .sorted(Comparator.comparing(Word::x))
                        .map(Word::text)
                        .collect(Collectors.joining(" "))
                        .trim();
                row[i] = value;
                if (!value.isEmpty()) {
                    nonEmptyColumns++;
                }
            }
            if (nonEmptyColumns < 2) {
                continue;
            }

            float rowTop = line.stream().map(Word::y).min(Float::compareTo).orElse(0f);
            float rowBottom = line.stream().map(word -> word.y() + word.height()).max(Float::compareTo).orElse(rowTop + 10f);
            List<Cell> outputRow = new ArrayList<>();

            for (int col = 0; col < bands.size(); col++) {
                List<Word> bucket = wordsByBand.get(col);
                float left = bucket.isEmpty()
                        ? bands.get(col)[0]
                        : bucket.stream().map(Word::x).min(Float::compareTo).orElse(bands.get(col)[0]);
                float right = bucket.isEmpty()
                        ? bands.get(col)[1]
                        : bucket.stream().map(word -> word.x() + word.width()).max(Float::compareTo).orElse(bands.get(col)[1]);
                Cell cell = new Cell(row[col], outputRowIndex, col, 1, 1,
                        left, rowTop, Math.max(0.5f, right - left), Math.max(0.5f, rowBottom - rowTop));
                outputRow.add(cell);
            }
            table.addRow(outputRow);
            outputRowIndex++;
        }

        return table.getRows().isEmpty() ? null : table;
    }

    private List<Rectangle2D.Float> resolveTableRegions(List<DetectedTable> tables) {
        if (tables == null || tables.isEmpty()) {
            return Collections.emptyList();
        }
        List<Rectangle2D.Float> regions = new ArrayList<>();
        for (DetectedTable table : tables) {
            Rectangle2D.Float region = toTableRegion(table);
            if (region != null) {
                regions.add(region);
            }
        }
        return regions;
    }

    private Rectangle2D.Float toTableRegion(DetectedTable table) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        for (List<Cell> row : table.getRows()) {
            for (Cell cell : row) {
                minX = Math.min(minX, cell.getX());
                minY = Math.min(minY, cell.getY());
                maxX = Math.max(maxX, cell.getX() + cell.getWidth());
                maxY = Math.max(maxY, cell.getY() + cell.getHeight());
            }
        }

        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY)) {
            return null;
        }

        return new Rectangle2D.Float(
                minX - TABLE_REGION_PADDING,
                minY - TABLE_REGION_PADDING,
                Math.max(0.5f, (maxX - minX) + (2 * TABLE_REGION_PADDING)),
                Math.max(0.5f, (maxY - minY) + (2 * TABLE_REGION_PADDING))
        );
    }
}
