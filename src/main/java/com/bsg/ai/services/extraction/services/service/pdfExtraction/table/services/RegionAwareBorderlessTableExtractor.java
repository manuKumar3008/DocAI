package com.bsg.ai.services.extraction.services.service.pdfExtraction.table.services;

import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Cell;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.DetectedTable;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.pojos.Word;
import com.bsg.ai.services.extraction.services.service.pdfExtraction.table.strippers.WordExtractor;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Deprecated
public class RegionAwareBorderlessTableExtractor {

    private static final float ROW_GROUP_TOLERANCE = 2.0f;
    private static final float REGION_VERTICAL_GAP = 25.0f;
    private static final int HISTOGRAM_BINS = 300;
    private static final float HORIZONTAL_BLOCK_MIN_GAP_RATIO = 0.10f;
    private static final float COLUMN_MERGE_GAP = 15.0f;
    private static final float COLUMN_ASSIGNMENT_TOLERANCE = 2.0f;
    private static final float TABLE_REGION_PADDING = 1.5f;
    private static final int MIN_BORDERLESS_TABLE_ROWS = 3;

    public List<DetectedTable> extractPageTables(PDDocument document,
                                                 int pageIndex,
                                                 List<DetectedTable> borderedTables,
                                                 int startingTableIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        float pageWidth = page.getMediaBox().getWidth();
        List<Rectangle2D.Float> excludedRegions = resolveTableRegions(borderedTables);

        List<Word> words = collectWords(document, pageIndex, excludedRegions);
        if (words.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Word>> lines = groupWordsByLine(words);
        List<List<List<Word>>> regions = splitIntoHorizontalRegions(lines);

        List<DetectedTable> tables = new ArrayList<>();
        int tableIndex = startingTableIndex;
        for (List<List<Word>> region : regions) {
            List<Word> regionWords = region.stream().flatMap(List::stream).collect(Collectors.toList());
            if (regionWords.isEmpty()) {
                continue;
            }

            List<float[]> horizontalBlocks = detectHorizontalBlocks(regionWords, pageWidth);
            for (float[] block : horizontalBlocks) {
                List<List<Word>> blockLines = filterLinesByXRange(region, block[0], block[1]);
                if (blockLines.size() < MIN_BORDERLESS_TABLE_ROWS) {
                    continue;
                }

                List<float[]> columnBands = detectColumnBands(blockLines, block[0], block[1] - block[0]);
                if (columnBands.size() < 2) {
                    continue;
                }

                DetectedTable table = tryExtractTable(blockLines, columnBands, pageIndex, tableIndex);
                if (table != null && !table.getRows().isEmpty()) {
                    tables.add(table);
                    tableIndex++;
                }
            }
        }
        return tables;
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
        Map<Float, List<Word>> lineMap = new LinkedHashMap<>();

        for (Word word : words) {
            Float match = lineMap.keySet().stream()
                    .filter(y -> Math.abs(y - word.y()) <= ROW_GROUP_TOLERANCE)
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                List<Word> list = new ArrayList<>();
                list.add(word);
                lineMap.put(word.y(), list);
            } else {
                lineMap.get(match).add(word);
            }
        }
        List<List<Word>> lines = new ArrayList<>(lineMap.values());
        for (List<Word> line : lines) {
            line.sort(Comparator.comparing(Word::x));
        }
        return lines;
    }

    private List<List<List<Word>>> splitIntoHorizontalRegions(List<List<Word>> lines) {
        List<List<List<Word>>> regions = new ArrayList<>();
        if (lines.isEmpty()) {
            return regions;
        }
        List<List<Word>> currentRegion = new ArrayList<>();
        currentRegion.add(lines.get(0));

        for (int i = 1; i < lines.size(); i++) {
            float previousY = lines.get(i - 1).get(0).y();
            float currentY = lines.get(i).get(0).y();
            if (currentY - previousY > REGION_VERTICAL_GAP) {
                regions.add(currentRegion);
                currentRegion = new ArrayList<>();
            }
            currentRegion.add(lines.get(i));
        }
        regions.add(currentRegion);
        return regions;
    }

    private List<float[]> detectHorizontalBlocks(List<Word> words, float pageWidth) {
        float binWidth = pageWidth / HISTOGRAM_BINS;
        boolean[] occupied = new boolean[HISTOGRAM_BINS];

        for (Word word : words) {
            int start = (int) (word.x() / binWidth);
            int end = (int) ((word.x() + word.width()) / binWidth);
            for (int i = Math.max(0, start); i <= Math.min(HISTOGRAM_BINS - 1, end); i++) {
                occupied[i] = true;
            }
        }

        List<float[]> blocks = new ArrayList<>();
        int start = -1;
        float minGapWidth = pageWidth * HORIZONTAL_BLOCK_MIN_GAP_RATIO;

        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            if (occupied[i]) {
                if (start == -1) {
                    start = i;
                }
            } else if (start != -1) {
                int gapSize = 0;
                while (i + gapSize < HISTOGRAM_BINS && !occupied[i + gapSize]) {
                    gapSize++;
                }
                if (gapSize * binWidth > minGapWidth) {
                    blocks.add(new float[]{start * binWidth, i * binWidth});
                    start = -1;
                    i += gapSize - 1;
                }
            }
        }
        if (start != -1) {
            blocks.add(new float[]{start * binWidth, pageWidth});
        }
        return blocks;
    }

    private List<List<Word>> filterLinesByXRange(List<List<Word>> lines, float minX, float maxX) {
        List<List<Word>> filtered = new ArrayList<>();
        for (List<Word> line : lines) {
            List<Word> inRange = line.stream()
                    .filter(word -> (word.x() + (word.width() / 2f)) >= minX
                            && (word.x() + (word.width() / 2f)) <= maxX)
                    .collect(Collectors.toList());
            if (!inRange.isEmpty()) {
                filtered.add(inRange);
            }
        }
        return filtered;
    }

    private List<float[]> detectColumnBands(List<List<Word>> lines, float minX, float width) {
        if (width <= 0f) {
            return Collections.emptyList();
        }
        float binWidth = width / HISTOGRAM_BINS;
        float[] histogram = new float[HISTOGRAM_BINS];

        for (List<Word> line : lines) {
            for (Word word : line) {
                if (StringUtils.isBlank(word.text())) {
                    continue;
                }
                int start = (int) ((word.x() - minX) / binWidth);
                int end = (int) (((word.x() + word.width()) - minX) / binWidth);
                for (int bin = Math.max(0, start); bin <= Math.min(HISTOGRAM_BINS - 1, end); bin++) {
                    histogram[bin]++;
                }
            }
        }

        List<float[]> bands = new ArrayList<>();
        int start = -1;
        for (int i = 0; i < HISTOGRAM_BINS; i++) {
            if (histogram[i] > 0) {
                if (start == -1) {
                    start = i;
                }
            } else if (start != -1) {
                bands.add(new float[]{minX + (start * binWidth), minX + (i * binWidth)});
                start = -1;
            }
        }
        if (start != -1) {
            bands.add(new float[]{minX + (start * binWidth), minX + width});
        }
        return mergeBands(bands, COLUMN_MERGE_GAP);
    }

    private List<float[]> mergeBands(List<float[]> bands, float gap) {
        if (bands.size() < 2) {
            return bands;
        }
        List<float[]> merged = new ArrayList<>();
        float[] current = bands.get(0).clone();
        for (int i = 1; i < bands.size(); i++) {
            if (bands.get(i)[0] - current[1] < gap) {
                current[1] = bands.get(i)[1];
            } else {
                merged.add(current);
                current = bands.get(i).clone();
            }
        }
        merged.add(current);
        return merged;
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
                    if (centerX >= bandStart - COLUMN_ASSIGNMENT_TOLERANCE
                            && centerX <= bandEnd + COLUMN_ASSIGNMENT_TOLERANCE) {
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

        return table.getRows().size() >= MIN_BORDERLESS_TABLE_ROWS ? table : null;
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
