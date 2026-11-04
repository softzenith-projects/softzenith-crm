package com.softzenith.crm.lead;

import com.softzenith.crm.shared.web.InvalidInputException;
import org.apache.commons.csv.CSVFormat;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads the first sheet of an .xlsx, or a .csv, into text rows. The header is the first row with any value in it
 * (exports often start with a blank or title row); every later non-blank row is a data row, numbered as in the file.
 */
final class SpreadsheetReader {

    static final int MAX_ROWS = 5_000;
    static final int MAX_COLUMNS = 50;

    record Sheet(List<String> headers, List<Row> rows) {
    }

    /** @param number the row number the spreadsheet shows (1-based), so an Admin can find it */
    record Row(int number, List<String> cells) {
    }

    private SpreadsheetReader() {
    }

    static Sheet read(String fileName, byte[] bytes) {
        var name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        List<Row> raw;
        if (name.endsWith(".xlsx")) {
            raw = xlsx(bytes);
        } else if (name.endsWith(".csv")) {
            raw = csv(bytes);
        } else if (name.endsWith(".xls")) {
            throw new InvalidInputException("Old Excel files (.xls) can't be read: open it in Excel and save it as .xlsx or .csv");
        } else {
            throw new InvalidInputException("Upload an Excel (.xlsx) or CSV (.csv) file");
        }
        if (raw.isEmpty()) {
            throw new InvalidInputException("The file is empty");
        }
        var header = raw.getFirst().cells();
        var width = header.size();
        while (width > 0 && header.get(width - 1).isBlank()) width--;
        if (width > MAX_COLUMNS) {
            throw new InvalidInputException("The file has more than " + MAX_COLUMNS + " columns");
        }
        var headers = new ArrayList<String>();
        for (var i = 0; i < width; i++) {
            var h = header.get(i).strip();
            headers.add(h.isEmpty() ? "Column " + (i + 1) : h);
        }
        var w = width;
        var rows = raw.subList(1, raw.size()).stream()
                .map(r -> new Row(r.number(), fit(r.cells(), w)))
                .filter(r -> r.cells().stream().anyMatch(c -> !c.isBlank()))
                .toList();
        if (rows.size() > MAX_ROWS) {
            throw new InvalidInputException("The file has more than " + MAX_ROWS + " rows: split it into smaller files");
        }
        return new Sheet(List.copyOf(headers), rows);
    }

    /** Non-blank rows, header included, stopping just past the limit so a huge file fails fast. */
    private static List<Row> xlsx(byte[] bytes) {
        var rows = new ArrayList<Row>();
        try (var workbook = new ReadableWorkbook(new ByteArrayInputStream(bytes));
             var stream = workbook.getFirstSheet().openStream()) {
            var it = stream.iterator();
            while (it.hasNext() && rows.size() <= MAX_ROWS + 1) {
                var row = it.next();
                var cells = new ArrayList<String>();
                for (var i = 0; i < row.getCellCount(); i++) {
                    cells.add(text(row.getCell(i)));
                }
                if (cells.stream().anyMatch(c -> !c.isBlank())) {
                    rows.add(new Row(row.getRowNum(), cells));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new InvalidInputException("The file could not be read as an Excel workbook (.xlsx)");
        }
        return rows;
    }

    private static List<Row> csv(byte[] bytes) {
        var content = decode(bytes);
        if (content.startsWith("﻿")) content = content.substring(1);
        var firstLine = content.lines().findFirst().orElse("");
        // Excel in many European locales writes ';' between values.
        var delimiter = firstLine.chars().filter(c -> c == ';').count() > firstLine.chars().filter(c -> c == ',').count() ? ';' : ',';
        var rows = new ArrayList<Row>();
        try (var parser = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).get().parse(new StringReader(content))) {
            for (var record : parser) {
                if (rows.size() > MAX_ROWS + 1) break;
                var cells = record.stream().map(c -> c == null ? "" : c).toList();
                if (cells.stream().anyMatch(c -> !c.isBlank())) {
                    rows.add(new Row((int) record.getRecordNumber(), cells));
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new InvalidInputException("The file could not be read as CSV");
        }
        return rows;
    }

    /** UTF-8 when it is valid UTF-8; otherwise Windows-1252, which is what Excel on Windows saves "CSV" as. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    /**
     * The cell as text. A mobile number typed as a number reads "9876543210" (or "9876543210.0" from some exports);
     * a trailing ".0" is dropped so it reads as the digits.
     */
    private static String text(Cell cell) {
        if (cell == null) return "";
        var text = cell.getText();
        if (text == null) return "";
        return text.matches("\\d+\\.0+") ? text.substring(0, text.indexOf('.')) : text;
    }

    private static List<String> fit(List<String> cells, int width) {
        var out = new ArrayList<String>(width);
        for (var i = 0; i < width; i++) {
            out.add(i < cells.size() ? cells.get(i) : "");
        }
        return out;
    }
}
