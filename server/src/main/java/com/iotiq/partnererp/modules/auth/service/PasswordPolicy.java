package com.iotiq.partnererp.modules.auth.service;

import com.iotiq.partnererp.common.exception.ValidationException;
import com.iotiq.partnererp.config.SecurityProperties;
import org.springframework.stereotype.Component;

/**
 * Password rules for new and changed passwords: length within the configured bounds (default 12-128),
 * at least one letter and one digit, and not the account's email address.
 */
@Component
public class PasswordPolicy {

    private final SecurityProperties.Password rules;

    public PasswordPolicy(SecurityProperties properties) {
        this.rules = properties.password();
    }

    public void validate(String field, String password, String email) {
        if (password == null || password.length() < rules.minLength() || password.length() > rules.maxLength()) {
            throw ValidationException.of(field, "Password must be " + rules.minLength() + " to "
                    + rules.maxLength() + " characters long.");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw ValidationException.of(field, "Password must contain at least one letter and one digit.");
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            throw ValidationException.of(field, "Password must not be the email address.");
        }
    }

    public String describe() {
        return rules.minLength() + "-" + rules.maxLength() + " characters with at least one letter and one digit";
    }
}
