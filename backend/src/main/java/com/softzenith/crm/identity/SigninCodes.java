package com.softzenith.crm.identity;

import java.util.Optional;

/** Rules for a staff sign-in code: 6 to 8 digits, not one repeated digit and not a straight run like 123456. */
public final class SigninCodes {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 8;

    private SigninCodes() {
    }

    /** Why the code is not acceptable, or empty when it is. */
    public static Optional<String> problem(String code) {
        if (code == null || !code.matches("\\d{" + MIN_LENGTH + "," + MAX_LENGTH + "}")) {
            return Optional.of("The sign-in code must be " + MIN_LENGTH + " to " + MAX_LENGTH + " digits");
        }
        if (code.chars().distinct().count() == 1 || isRun(code, 1) || isRun(code, -1)) {
            return Optional.of("The sign-in code is too easy to guess; avoid repeated digits and runs like 123456");
        }
        return Optional.empty();
    }

    private static boolean isRun(String code, int step) {
        for (int i = 1; i < code.length(); i++) {
            if (code.charAt(i) - code.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }
}
