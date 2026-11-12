import { describe, expect, it } from "vitest";
import { isOther, otherProblem } from "./ChoiceField";

describe("isOther", () => {
  it("matches the business's 'Other' entry however it is written", () => {
    expect(["Other", "other", " OTHERS "].every(isOther)).toBe(true);
    expect(["Otherwise", "Mother", ""].some(isOther)).toBe(false);
  });
});

describe("otherProblem", () => {
  it("accepts real names, with numbers and simple punctuation", () => {
    for (const ok of ["Australia", "St. Kitts & Nevis", "German A2", "B.Sc / M.Sc", "Côte d’Ivoire"]) {
      expect(otherProblem(ok, "Australia")).toBe("");
    }
  });

  it("refuses empty, too short, filler, one key held down, mostly digits and odd symbols", () => {
    expect(otherProblem("  ", "Australia")).toMatch(/type it in/);
    expect(otherProblem("A", "Australia")).toMatch(/too short/);
    for (const filler of ["na", "N/A", "other", "Don't know", "aaaa", "AB123456", "xyz"]) {
      expect(otherProblem(filler, "Australia")).toMatch(/does not say much/);
    }
    expect(otherProblem("Canada!!", "Australia")).toMatch(/simple punctuation/);
  });
});
