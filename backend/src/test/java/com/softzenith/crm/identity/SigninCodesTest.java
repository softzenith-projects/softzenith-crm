package com.softzenith.crm.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SigninCodesTest {

    @Test
    void sixToEightDigitsThatAreNotObvious() {
        assertThat(SigninCodes.problem("482917")).isEmpty();
        assertThat(SigninCodes.problem("48291735")).isEmpty();
        assertThat(SigninCodes.problem("112233")).isEmpty(); // repeats, but not one digit throughout
    }

    @Test
    void wrongLengthOrNotDigitsIsRefused() {
        for (var bad : new String[]{null, "", "48291", "482917351", "48a917", " 482917", "४८२९१७"}) {
            assertThat(SigninCodes.problem(bad)).as(String.valueOf(bad)).hasValueSatisfying(p -> assertThat(p).contains("6 to 8 digits"));
        }
    }

    @Test
    void repeatedDigitsAndStraightRunsAreRefused() {
        for (var easy : new String[]{"000000", "777777", "123456", "012345", "23456789", "654321", "98765432"}) {
            assertThat(SigninCodes.problem(easy)).as(easy).hasValueSatisfying(p -> assertThat(p).contains("too easy"));
        }
    }
}
