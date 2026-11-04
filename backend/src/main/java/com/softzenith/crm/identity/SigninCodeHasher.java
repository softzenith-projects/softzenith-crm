package com.softzenith.crm.identity;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Hashes and checks staff sign-in codes (bcrypt, slow on purpose: a stolen table does not give the codes away). */
@Component
class SigninCodeHasher {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    String hash(String code) {
        return encoder.encode(code);
    }

    boolean matches(String code, String hash) {
        return code != null && hash != null && encoder.matches(code, hash);
    }
}
