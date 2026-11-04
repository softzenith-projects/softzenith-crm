import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { freshName, freshPhone, signIn } from "./helpers";

const shot = async (page: Page, info: TestInfo, name: string) =>
  info.attach(name, { body: await page.screenshot({ path: info.outputPath(`${name}.png`) }), contentType: "image/png" });

/**
 * A business onboarded without Team & branches (the first handover to a client): its Admin signs in, sees every lead and
 * works it, and nothing shows that other roles, staff or branches exist.
 */
test("the admin of a business without Team & branches runs every lead and sees no other roles", async ({ page }, info) => {
  const stamp = String(Date.now()).slice(-6);
  const slug = `solo-${stamp}`;
  const business = `Solo Consultancy ${stamp}`;
  const adminPhone = freshPhone();

  await test.step("the platform admin onboards the business with Team & branches off", async () => {
    await page.goto("/platform");
    await page.getByLabel("Username").fill("softzenith");
    await page.getByLabel("Password").fill("local-platform-admin");
    await page.getByRole("button", { name: "Sign in" }).click();
    await page.getByRole("link", { name: "Onboard a business" }).click();

    await page.getByLabel("Business name *").fill(business);
    await page.getByLabel("Web address *").fill(slug);
    await page.getByRole("button", { name: /^Next/ }).click();

    await page.getByLabel(/Lead number prefix/).fill("SOL");
    await page.getByLabel(/Services offered/).fill("Study Visa");
    await page.getByRole("checkbox", { name: /Team & branches/ }).uncheck();
    await page.getByRole("button", { name: /^Next/ }).click();
    await page.getByRole("button", { name: /^Next/ }).click(); // no branches

    await page.getByRole("button", { name: "Add person" }).click();
    await page.getByLabel("Row 1 name").fill(freshName(adminPhone));
    await page.getByLabel("Row 1 mobile").fill(adminPhone);
    await page.getByRole("button", { name: /^Next/ }).click();

    page.once("dialog", (dialog) => dialog.accept());
    await page.getByRole("button", { name: "Go live", exact: true }).click();
    await expect(page.getByRole("heading", { name: `${business} is live` })).toBeVisible();
  });

  await test.step("a student enquires on the business's form", async () => {
    await page.goto(`/enquiry/${slug}`);
    await page.getByLabel("Full name *").fill("First Student");
    await page.getByLabel("Mobile number *").fill(freshPhone());
    await page.getByRole("button", { name: "Submit enquiry" }).click();
    await expect(page.getByRole("heading", { name: /will get in touch/ })).toBeVisible();
  });

  await test.step("the admin signs in and the menu has only what they need", async () => {
    await signIn(page, adminPhone);
    const menu = page.getByRole("navigation").first();
    await expect(menu.getByRole("link", { name: "Dashboard" })).toBeVisible();
    await expect(menu.getByRole("link", { name: "Leads" })).toBeVisible();
    await expect(menu.getByRole("link", { name: "Staff" })).toHaveCount(0);
    await expect(menu.getByRole("link", { name: "Branches" })).toHaveCount(0);
  });

  await test.step("typing the staff or branches address leads back to the leads", async () => {
    for (const path of ["/admin/staff", "/admin/branches"]) {
      await page.goto(path);
      await expect(page).toHaveURL(/\/leads$/);
    }
  });

  await test.step("the lead list shows the lead and nothing about assignment", async () => {
    await page.goto("/leads");
    await expect(page.getByText("SOL-000001")).toBeVisible();
    await expect(page.getByRole("columnheader", { name: "Assigned to" })).toHaveCount(0);
    await expect(page.getByRole("columnheader", { name: "Branch" })).toHaveCount(0);
    await expect(page.getByText("Unassigned only")).toHaveCount(0);
    await shot(page, info, "leads");
  });

  await test.step("the add-lead form asks nothing about a branch", async () => {
    await page.getByRole("button", { name: "Add lead" }).first().click();
    await expect(page.getByRole("heading", { name: "Add a lead" })).toBeVisible();
    await expect(page.getByLabel("Branch")).toHaveCount(0);
    await page.keyboard.press("Escape");
  });

  await test.step("the admin contacts, closes and reopens the lead", async () => {
    await page.getByRole("link", { name: "First Student" }).click();
    await expect(page.getByRole("heading", { name: "First Student" })).toBeVisible();
    await expect(page.getByLabel("Assign to")).toHaveCount(0);
    await expect(page.getByText("Assigned to")).toHaveCount(0);
    await shot(page, info, "lead-detail");

    await page.getByRole("button", { name: "Update status" }).click();
    await expect(page.getByText("Status updated")).toBeVisible();

    await page.getByLabel("Change status to").selectOption("CLOSED");
    await page.getByPlaceholder("e.g. Not interested").fill("Went elsewhere");
    await page.getByRole("button", { name: "Update status" }).click();
    await expect(page.getByText("Went elsewhere", { exact: false }).first()).toBeVisible();

    await page.getByRole("button", { name: "Reopen" }).click();
    await expect(page.getByText("Lead reopened")).toBeVisible();
  });

  await test.step("the dashboard counts the leads and mentions no team", async () => {
    await page.getByRole("navigation").first().getByRole("link", { name: "Dashboard" }).click();
    await expect(page.getByText("Leads received")).toBeVisible();
    await expect(page.getByText("By source")).toBeVisible();
    await expect(page.getByText("Team workload")).toHaveCount(0);
    await expect(page.getByText("Open & unassigned")).toHaveCount(0);
    await expect(page.getByText(/counsellor/i)).toHaveCount(0);
    await shot(page, info, "dashboard");
  });
});
