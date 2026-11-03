package com.softzenith.crm.lead;

import com.softzenith.crm.shared.phone.PhoneNumbers;
import com.softzenith.crm.shared.tenant.TenantContext;
import com.softzenith.crm.shared.text.Text;
import com.softzenith.crm.shared.web.InvalidInputException;
import com.softzenith.crm.tenancy.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lead import from a spreadsheet (IMPORT1): read the file, suggest which lead field each column holds, then check or
 * import the rows. Checking writes nothing; importing re-checks every row, so it never trusts an earlier check. Rows
 * are saved in small transactions, so one bad chunk can't undo the rest. Imported leads are not announced (see
 * {@link LeadService#importLead}).
 */
@Service
public class LeadImports {

    private static final Logger log = LoggerFactory.getLogger(LeadImports.class);

    private static final int CHUNK = 200;
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern NAME = Pattern.compile(NewLead.NAME_PATTERN);

    /** The lead field a column goes to. EXTRA keeps it with the lead under the header's name; IGNORE drops it. */
    public enum ImportField {
        FULL_NAME, PHONE, EMAIL, SERVICE_INTEREST, PREFERRED_COUNTRY, MESSAGE, EXTERNAL_REF, EXTRA, IGNORE;

        /** At most one column per field; EXTRA and IGNORE may repeat. */
        boolean single() {
            return this != EXTRA && this != IGNORE;
        }
    }

    public enum ImportOutcome { ADDED, ALREADY_IN_CRM, DUPLICATE_IN_FILE, REJECTED }

    /** A file read and matched, for the Admin to check. */
    public record ImportPreview(String fileName, List<String> headers, List<ImportField> columns, List<ImportRow> rows) {
    }

    public record ImportRow(int row, List<String> cells) {
    }

    /**
     * @param columns one target per header, in order
     * @param values  per field, spreadsheet value → the business's option (e.g. "IELTS" → "IELTS / PTE Coaching")
     */
    public record ImportRequest(String fileName, String sourceLabel, List<String> headers, List<ImportField> columns,
                                List<ImportRow> rows, Map<ImportField, Map<String, String>> values) {
    }

    /** @param problems every row that was not (or would not be) added, in file order */
    public record ImportReport(boolean dryRun, int total, int added, int alreadyInCrm, int duplicates, int rejected,
                               List<ImportRowResult> problems) {
    }

    /** @param detail why it was not added: the reason, the existing lead number or the earlier row */
    public record ImportRowResult(int row, String name, String phone, ImportOutcome outcome, String detail) {
    }

    private final LeadService leads;
    private final LeadImportColumnRepository savedColumns;
    private final TenantRepository tenants;
    private final TransactionTemplate tx;

    LeadImports(LeadService leads, LeadImportColumnRepository savedColumns, TenantRepository tenants,
                PlatformTransactionManager transactions) {
        this.leads = leads;
        this.savedColumns = savedColumns;
        this.tenants = tenants;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Reads the file and suggests a target per column: the business's last choice for that header, else a guess. */
    public ImportPreview read(String fileName, byte[] bytes) {
        var sheet = SpreadsheetReader.read(fileName, bytes);
        var saved = tx.execute(s -> savedColumns.findAll().stream()
                .collect(Collectors.toMap(LeadImportColumn::getHeaderKey, LeadImportColumn::getTarget, (a, b) -> a)));
        var columns = suggest(sheet.headers(), saved);
        log.info("Import file read: {} columns, {} rows", sheet.headers().size(), sheet.rows().size());
        return new ImportPreview(fileName, sheet.headers(), columns,
                sheet.rows().stream().map(r -> new ImportRow(r.number(), r.cells())).toList());
    }

    static List<ImportField> suggest(List<String> headers, Map<String, ImportField> saved) {
        var used = EnumSet.noneOf(ImportField.class);
        var out = new ArrayList<ImportField>();
        for (var header : headers) {
            var key = headerKey(header);
            var target = saved.getOrDefault(key, guess(key));
            if (target.single() && !used.add(target)) {
                target = ImportField.EXTRA;
            }
            out.add(target);
        }
        return out;
    }

    /** "Mobile No." → "mobileno": how headers are compared and saved. */
    static String headerKey(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static ImportField guess(String key) {
        if (key.matches("(sr|s|sl|serial)?no|sr|srno|serialnumber|sno|slno|rowno|index|id")) return ImportField.IGNORE;
        if (key.contains("email") || key.contains("mail")) return ImportField.EMAIL;
        if (key.matches(".*(mobile|phone|contact|whatsapp|cell|mob).*")) return ImportField.PHONE;
        if (key.matches(".*(country|destination).*")) return ImportField.PREFERRED_COUNTRY;
        if (key.matches(".*(enquiry|inquiry|service|interest|course|purpose).*")) return ImportField.SERVICE_INTEREST;
        if (key.matches(".*(message|remark|comment|note|query).*")) return ImportField.MESSAGE;
        if (key.matches("(full|student|candidate|customer|lead|client|applicant)?name|name.*")) return ImportField.FULL_NAME;
        return ImportField.EXTRA;
    }

    /** Checks every row (dryRun) or imports it. */
    public ImportReport run(ImportRequest r, boolean dryRun) {
        validateShape(r);
        var tenant = tenants.findById(TenantContext.require()).orElseThrow();
        var region = tenant.getDefaultRegion();
        var label = Text.singleLine(r.sourceLabel());
        var sourceDetail = label == null ? "Import" : label.length() > 100 ? label.substring(0, 100) : label;
        var fileName = Text.singleLine(r.fileName()) == null ? "a spreadsheet" : Text.singleLine(r.fileName());
        var extraKeys = extraKeys(r.headers(), r.columns());
        var values = r.values() == null ? Map.<ImportField, Map<String, String>>of() : r.values();

        var results = new ArrayList<ImportRowResult>();
        var valid = new ArrayList<Valid>();
        var seen = new HashMap<String, Integer>();
        for (var row : r.rows()) {
            var v = parse(row, r.columns(), extraKeys, values, region, sourceDetail);
            if (v instanceof Invalid(var name, var phone, var reason)) {
                results.add(new ImportRowResult(row.row(), name, phone, ImportOutcome.REJECTED, reason));
                continue;
            }
            var ok = (Valid) v;
            var earlier = seen.putIfAbsent(ok.phoneE164() + "|" + (ok.lead().email() == null ? "" : ok.lead().email().toLowerCase(Locale.ROOT)), row.row());
            if (earlier != null) {
                results.add(new ImportRowResult(row.row(), ok.lead().fullName(), ok.phoneE164(), ImportOutcome.DUPLICATE_IN_FILE,
                        "Same person as row " + earlier));
                continue;
            }
            valid.add(ok);
        }

        if (dryRun) {
            for (var ok : valid) {
                var existing = leads.openLeadNumber(ok.phoneE164(), ok.lead().email());
                results.add(existing.map(n -> new ImportRowResult(ok.row(), ok.lead().fullName(), ok.phoneE164(), ImportOutcome.ALREADY_IN_CRM, n))
                        .orElseGet(() -> new ImportRowResult(ok.row(), ok.lead().fullName(), ok.phoneE164(), ImportOutcome.ADDED, null)));
            }
        } else {
            for (var from = 0; from < valid.size(); from += CHUNK) {
                var chunk = valid.subList(from, Math.min(from + CHUNK, valid.size()));
                try {
                    results.addAll(tx.execute(s -> chunk.stream().map(ok -> save(ok, fileName)).toList()));
                } catch (RuntimeException e) {
                    log.error("Import: {} rows from row {} could not be saved", chunk.size(), chunk.getFirst().row(), e);
                    chunk.forEach(ok -> results.add(new ImportRowResult(ok.row(), ok.lead().fullName(), ok.phoneE164(),
                            ImportOutcome.REJECTED, "Could not be saved; try this row again")));
                }
            }
            tx.executeWithoutResult(s -> remember(r.headers(), r.columns()));
        }
        return report(dryRun, r.rows().size(), results);
    }

    private ImportRowResult save(Valid ok, String fileName) {
        var result = leads.importLead(ok.lead(), "Imported from " + fileName + ", row " + ok.row());
        return result.created()
                ? new ImportRowResult(ok.row(), ok.lead().fullName(), ok.phoneE164(), ImportOutcome.ADDED, result.lead().getLeadNumber())
                : new ImportRowResult(ok.row(), ok.lead().fullName(), ok.phoneE164(), ImportOutcome.ALREADY_IN_CRM, result.lead().getLeadNumber());
    }

    private ImportReport report(boolean dryRun, int total, List<ImportRowResult> results) {
        var counts = results.stream().collect(Collectors.groupingBy(ImportRowResult::outcome, Collectors.counting()));
        Function<ImportOutcome, Integer> count = o -> counts.getOrDefault(o, 0L).intValue();
        var problems = results.stream().filter(x -> x.outcome() != ImportOutcome.ADDED)
                .sorted(Comparator.comparingInt(ImportRowResult::row)).toList();
        var report = new ImportReport(dryRun, total, count.apply(ImportOutcome.ADDED), count.apply(ImportOutcome.ALREADY_IN_CRM),
                count.apply(ImportOutcome.DUPLICATE_IN_FILE), count.apply(ImportOutcome.REJECTED), problems);
        log.info("Import {}: {} rows, {} added, {} already in CRM, {} duplicates, {} rejected", dryRun ? "check" : "done",
                total, report.added(), report.alreadyInCrm(), report.duplicates(), report.rejected());
        return report;
    }

    private void remember(List<String> headers, List<ImportField> columns) {
        var existing = savedColumns.findAll().stream()
                .collect(Collectors.toMap(LeadImportColumn::getHeaderKey, c -> c, (a, b) -> a));
        for (var i = 0; i < headers.size(); i++) {
            var key = headerKey(headers.get(i));
            if (key.isEmpty() || key.length() > 200) continue;
            var column = existing.get(key);
            if (column == null) {
                existing.put(key, savedColumns.save(new LeadImportColumn(key, columns.get(i))));
            } else {
                column.setTarget(columns.get(i));
            }
        }
    }

    private sealed interface Checked permits Valid, Invalid {
    }

    private record Valid(int row, NewLead lead, String phoneE164) implements Checked {
    }

    private record Invalid(String name, String phone, String reason) implements Checked {
    }

    private static Checked parse(ImportRow row, List<ImportField> columns, Map<Integer, String> extraKeys,
                                 Map<ImportField, Map<String, String>> values, String region, String sourceDetail) {
        var fields = new HashMap<ImportField, String>();
        var extra = new LinkedHashMap<String, Object>();
        for (var i = 0; i < columns.size(); i++) {
            var cell = i < row.cells().size() ? Text.singleLine(row.cells().get(i)) : null;
            var target = columns.get(i);
            if (cell == null || target == ImportField.IGNORE) continue;
            if (target == ImportField.EXTRA) {
                extra.put(extraKeys.get(i), cell);
            } else {
                var mapped = values.getOrDefault(target, Map.of()).get(cell);
                fields.put(target, mapped == null || mapped.isBlank() ? cell : mapped.strip());
            }
        }
        var name = fields.get(ImportField.FULL_NAME);
        var phone = fields.get(ImportField.PHONE);
        var email = fields.get(ImportField.EMAIL);

        String reason = null;
        if (name == null) reason = "No name";
        else if (name.length() > 200 || !NAME.matcher(name).matches()) reason = "Name " + NewLead.NAME_MESSAGE;
        else if (phone == null) reason = "No mobile number";
        else if (email != null && (email.length() > 254 || !EMAIL.matcher(email).matches())) reason = "Not an email address: " + email;
        else if (tooLong(fields, ImportField.SERVICE_INTEREST, 200) || tooLong(fields, ImportField.PREFERRED_COUNTRY, 100)
                || tooLong(fields, ImportField.MESSAGE, 2000) || tooLong(fields, ImportField.EXTERNAL_REF, 200)) reason = "A value is too long";
        else if (String.valueOf(extra).length() > 10_000) reason = "The extra columns hold more than 10 KB";
        if (reason != null) {
            return new Invalid(name, phone, reason);
        }
        String e164;
        try {
            e164 = PhoneNumbers.toE164(phone, region);
        } catch (InvalidInputException ex) {
            return new Invalid(name, phone, "Not a valid mobile number: " + phone);
        }
        var lead = new NewLead(name, phone, email, fields.get(ImportField.SERVICE_INTEREST),
                fields.get(ImportField.PREFERRED_COUNTRY), fields.get(ImportField.MESSAGE), null, LeadSourceType.IMPORT,
                sourceDetail, null, null, null, fields.get(ImportField.EXTERNAL_REF), extra);
        return new Valid(row.row(), lead, e164);
    }

    private static boolean tooLong(Map<ImportField, String> fields, ImportField target, int max) {
        var v = fields.get(target);
        return v != null && v.length() > max;
    }

    /** Custom-field key per EXTRA column: the header (64 characters at most), made unique. */
    private static Map<Integer, String> extraKeys(List<String> headers, List<ImportField> columns) {
        var keys = new HashMap<Integer, String>();
        var used = new HashSet<String>();
        for (var i = 0; i < headers.size(); i++) {
            if (columns.get(i) != ImportField.EXTRA) continue;
            var base = Text.singleLine(headers.get(i));
            base = base == null ? "Column " + (i + 1) : base.length() > 60 ? base.substring(0, 60) : base;
            var key = base;
            for (var n = 2; !used.add(key); n++) key = base + " " + n;
            keys.put(i, key);
        }
        return keys;
    }

    private static void validateShape(ImportRequest r) {
        if (r.headers() == null || r.columns() == null || r.rows() == null || r.headers().size() != r.columns().size()) {
            throw new InvalidInputException("Give one field for each column");
        }
        if (r.headers().size() > SpreadsheetReader.MAX_COLUMNS || r.rows().size() > SpreadsheetReader.MAX_ROWS) {
            throw new InvalidInputException("At most " + SpreadsheetReader.MAX_ROWS + " rows and "
                    + SpreadsheetReader.MAX_COLUMNS + " columns per file");
        }
        if (r.columns().stream().anyMatch(Objects::isNull)) {
            throw new InvalidInputException("Give one field for each column");
        }
        var singles = r.columns().stream().filter(ImportField::single).toList();
        if (singles.size() != Set.copyOf(singles).size()) {
            throw new InvalidInputException("Each field can come from one column only");
        }
        if (!singles.contains(ImportField.FULL_NAME) || !singles.contains(ImportField.PHONE)) {
            throw new InvalidInputException("Choose the column that holds the name and the one that holds the mobile number");
        }
        if (r.rows().stream().anyMatch(row -> row.cells() == null || row.cells().size() > SpreadsheetReader.MAX_COLUMNS
                || row.cells().stream().anyMatch(c -> c != null && c.length() > 5_000))) {
            throw new InvalidInputException("A row is too large");
        }
    }
}
