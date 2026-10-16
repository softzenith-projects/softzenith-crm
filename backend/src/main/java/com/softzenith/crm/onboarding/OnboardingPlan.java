package com.softzenith.crm.onboarding;

import java.util.List;

/**
 * The answer to "what would going live do?": either the problems to fix, or a summary of what will be created.
 *
 * @param ready    true when the blueprint passed validation and a full trial run (rolled back)
 * @param warnings things that work but are probably not intended (e.g. a branch manager without a branch)
 * @param summary  what will be created; null while there are problems
 */
public record OnboardingPlan(boolean ready, List<OnboardingProblem> problems, List<String> warnings, OnboardingSummary summary) {

    /** @param field where the problem is, e.g. {@code staff[2].phone}; null for the whole blueprint */
    public record OnboardingProblem(String field, String message) {
    }

    public record OnboardingSummary(String name, String slug, String defaultRegion, String timezone, String leadNumberExample,
                          String publicFormPath, List<String> roles, List<String> branches, List<OnboardingStaffLine> staff,
                          List<String> features) {
    }

    public record OnboardingStaffLine(String fullName, String phone, String role, String branch, boolean hasSigninCode) {
    }
}
