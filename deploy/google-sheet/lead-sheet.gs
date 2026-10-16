/**
 * AimDesk → Google Sheet. Receives each enquiry from AimDesk and adds it as a row to the first tab.
 *
 * Set-up (docs/lead-sheet.md):
 *  1. In the sheet: Extensions → Apps Script, replace everything with this file, save.
 *  2. Deploy → New deployment → Web app: Execute as "Me", Who has access "Anyone" → Deploy → copy the Web app URL.
 *  3. Project Settings (gear) → Script properties → add SECRET = the secret AimDesk gives you.
 */

function doPost(e) {
  var body;
  try {
    body = JSON.parse(e.postData.contents);
  } catch (err) {
    return reply({ ok: false, error: 'not JSON' });
  }
  var secret = PropertiesService.getScriptProperties().getProperty('SECRET');
  if (!secret || body.secret !== secret) {
    return reply({ ok: false, error: 'wrong secret' });
  }

  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sheet = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(body.columns);
      sheet.getRange(1, 1, 1, body.columns.length).setFontWeight('bold');
      sheet.setFrozenRows(1);
    }
    // AimDesk may send the same enquiry twice after a retry: skip it if its Ref is already in the sheet.
    var refColumn = body.columns.indexOf('Ref') + 1;
    if (refColumn > 0 && sheet.getLastRow() > 1) {
      var found = sheet.getRange(2, refColumn, sheet.getLastRow() - 1, 1)
        .createTextFinder(body.ref).matchEntireCell(true).findNext();
      if (found) {
        return reply({ ok: true, duplicate: true });
      }
    }
    sheet.appendRow(body.row.map(asText));
  } finally {
    lock.releaseLock();
  }
  return reply({ ok: true });
}

/** Keeps values as typed: "+91…" stays a phone number and "=…" never becomes a formula. */
function asText(value) {
  var text = value == null ? '' : String(value);
  return /^[=+\-@]/.test(text) ? "'" + text : text;
}

function reply(object) {
  return ContentService.createTextOutput(JSON.stringify(object)).setMimeType(ContentService.MimeType.JSON);
}
