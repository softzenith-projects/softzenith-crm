package com.softzenith.crm.lead;

import com.softzenith.crm.shared.web.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpreadsheetReaderTest {

    @Test
    void csvHeaderIsTheFirstFilledRowAndBlankHeadersGetANumber() {
        var sheet = SpreadsheetReader.read("a.CSV", bytes("﻿,,\nName,,Mobile,\nAsha,x,9876500001\n,,\nRavi\n"));
        assertThat(sheet.headers()).containsExactly("Name", "Column 2", "Mobile");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).cells()).containsExactly("Asha", "x", "9876500001");
        assertThat(sheet.rows().get(1).cells()).containsExactly("Ravi", "", "");
        assertThat(sheet.rows().get(1).number()).isEqualTo(5);
    }

    @Test
    void refusesFilesItCannotRead() {
        assertThatThrownBy(() -> SpreadsheetReader.read("old.xls", new byte[1])).hasMessageContaining("save it as .xlsx");
        assertThatThrownBy(() -> SpreadsheetReader.read("notes.txt", new byte[1])).hasMessageContaining("Upload an Excel");
        assertThatThrownBy(() -> SpreadsheetReader.read(null, new byte[1])).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> SpreadsheetReader.read("blank.csv", bytes(",,\n\n"))).hasMessage("The file is empty");
        assertThatThrownBy(() -> SpreadsheetReader.read("bad.csv", bytes("Name\n\"unclosed")))
                .hasMessageContaining("could not be read as CSV");
    }

    @Test
    void refusesTooManyColumnsOrRows() {
        var wide = String.join(",", java.util.Collections.nCopies(SpreadsheetReader.MAX_COLUMNS + 1, "h"));
        assertThatThrownBy(() -> SpreadsheetReader.read("wide.csv", bytes(wide))).hasMessageContaining("columns");

        var tall = new StringBuilder("Name\n");
        for (var i = 0; i <= SpreadsheetReader.MAX_ROWS + 5; i++) tall.append("p").append(i).append('\n');
        assertThatThrownBy(() -> SpreadsheetReader.read("tall.csv", bytes(tall.toString()))).hasMessageContaining("rows");
    }

    @Test
    void readsWindowsEncodedCsv() {
        var sheet = SpreadsheetReader.read("w.csv", "Name\nJosé\n".getBytes(java.nio.charset.Charset.forName("windows-1252")));
        assertThat(sheet.rows().getFirst().cells()).isEqualTo(List.of("José"));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
