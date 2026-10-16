import { expect, test } from "@playwright/test";
import { signIn, staff } from "./helpers";

/** AUTH1 in the browser: an Admin sets a staff member's sign-in code, and that person signs in with mobile + code. */

const DEMO_CODE = "482917"; // every demo staff member's code (backend devdata/DevDataSeeder)
const priya = "9000000007"; // Counsellor, Bahadurgarh

test("an admin sets a sign-in code and the counsellor signs in with it", async ({ page }) => {
  await signIn(page, staff.anuj);
  await page.goto("/admin/staff");
  const row = page.getByRole("row").filter({ hasText: "Priya" });
  await row.getByRole("button", { name: "Actions" }).click();
  await page.getByRole("menuitem", { name: /sign-in code/ }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel(/Sign-in code/).fill(DEMO_CODE); // the same code again, so the demo data stays as documented
  await dialog.getByRole("button", { name: "Save code" }).click();
  await expect(dialog.getByText("Sign-in code saved")).toBeVisible();
  await expect(dialog.getByText(DEMO_CODE)).toBeVisible();
  await dialog.getByRole("button", { name: "Done" }).click();
  await expect(row.getByText(/^Set/)).toBeVisible();

  await page.getByText("Anuj").first().click();
  await page.getByText("Sign out").click();
  await page.getByLabel("Mobile number").fill(priya);
  await page.getByLabel(/Sign-in code/).fill(DEMO_CODE);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Priya").first()).toBeVisible();
  await expect(page.getByRole("navigation").first()).toBeVisible();
});

test("a wrong code is refused with a plain message", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Mobile number").fill(staff.natasha);
  await page.getByLabel(/Sign-in code/).fill("739164");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByText("Wrong mobile number or sign-in code")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});
