package com.softzenith.crm.lead;

import com.softzenith.crm.lead.LeadImports.ImportField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LeadImportsTest {

    @Test
    void headerKeyKeepsOnlyLettersAndDigits() {
        assertThat(LeadImports.headerKey("Mobile No.")).isEqualTo("mobileno");
        assertThat(LeadImports.headerKey("Enquiry for( IELTS/PTE/Visa)")).isEqualTo("enquiryforieltsptevisa");
    }

    @Test
    void guessesCommonHeaders() {
        assertThat(LeadImports.suggest(List.of("S. No", "Student Name", "WhatsApp Number", "E-mail", "Course", "Destination",
                "Remarks", "Father's name"), Map.of()))
                .containsExactly(ImportField.IGNORE, ImportField.FULL_NAME, ImportField.PHONE, ImportField.EMAIL, ImportField.SERVICE_INTEREST,
                        ImportField.PREFERRED_COUNTRY, ImportField.MESSAGE, ImportField.EXTRA);
    }

    @Test
    void aFieldComesFromOneColumnOnly() {
        assertThat(LeadImports.suggest(List.of("Name", "Mobile", "Alternate mobile"), Map.of()))
                .containsExactly(ImportField.FULL_NAME, ImportField.PHONE, ImportField.EXTRA);
    }

    @Test
    void theBusinessesLastChoiceWins() {
        assertThat(LeadImports.suggest(List.of("Name", "Address"), Map.of("address", ImportField.IGNORE)))
                .containsExactly(ImportField.FULL_NAME, ImportField.IGNORE);
    }
}
