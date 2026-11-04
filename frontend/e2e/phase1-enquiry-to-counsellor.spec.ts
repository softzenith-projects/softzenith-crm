import { expect, test } from "@playwright/test";
import { TENANT, freshName, freshPhone, signIn, staff } from "./helpers";

/**
 * Phase 1 in the browser: a student enquires on the public form, reception finds and assigns it, the counsellor works
 * it, and admin closes and reopens it (back to the unassigned queue).
 */
test("an enquiry travels from the website to a counsellor and back to reception", async ({ page }) => {
  const phone = freshPhone();
  const name = freshName(phone);

  await test.step("the student sends an enquiry", async () => {
    await page.goto(`/enquiry/${TENANT}`);
    await page.getByLabel("Full name *").fill(name);
    await page.getByLabel("Mobile number *").fill(phone);
    await page.getByLabel("Message").fill("Looking for a January intake");
    await page.getByRole("button", { name: "Submit enquiry" }).click();
    await expect(page.getByRole("heading", { name: /will get in touch/ })).toBeVisible();
  });

  await test.step("reception finds it in the unassigned queue and assigns Natasha", async () => {
    await signIn(page, staff.frontDesk);
    await page.goto(`/leads?unassigned=true&q=${phone}`);
    await page.getByRole("link", { name }).click();
    await expect(page.getByRole("heading", { name })).toBeVisible();
    await page.getByLabel("Assign to").selectOption({ label: "Natasha · Counsellor · Rohtak" });
    await page.getByRole("button", { name: "Assign" }).click();
    await expect(page.getByText("Assigned", { exact: true }).first()).toBeVisible();
    await expect(page.getByLabel("Change status to")).toHaveCount(0); // reception cannot change status
  });

  await test.step("Natasha sees it in her list and marks it contacted", async () => {
    await signIn(page, staff.natasha);
    await page.goto(`/leads?q=${phone}`);
    await page.getByRole("link", { name }).click();
    await page.getByLabel("Change status to").selectOption("CONTACTED");
    await page.getByRole("button", { name: "Update status" }).click();
    await expect(page.getByText("Contacted", { exact: true }).first()).toBeVisible();
  });

  await test.step("admin closes it with a reason, then reopens it into the unassigned queue", async () => {
    await signIn(page, staff.anuj);
    await page.goto(`/leads?q=${phone}`);
    await page.getByRole("link", { name }).click();
    await page.getByLabel("Change status to").selectOption("CLOSED");
    await page.getByLabel("Reason *").fill("E2E: chose another agency");
    await page.getByRole("button", { name: "Update status" }).click();
    await expect(page.getByText(/^Closed .*: E2E: chose another agency/)).toBeVisible(); // also in the Activity list

    await page.getByRole("button", { name: "Reopen" }).click();
    await expect(page.getByText("Unassigned", { exact: true }).first()).toBeVisible();
    await page.goto(`/leads?unassigned=true&q=${phone}`);
    await expect(page.getByRole("link", { name })).toBeVisible();
  });
});
