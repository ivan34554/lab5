package com.example.lab5.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.example.lab5.exception.BadRequestException;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = Set.of(
            "password1!", "password123!", "qwerty123!", "qwerty12345!", "1q2w3e4r5t!",
            "welcome123!", "admin12345!", "letmein123!", "iloveyou123!", "changeme123!");

    public static void require(String username, String password) {
        List<String> violations = violations(username, password);
        if (!violations.isEmpty()) {
            throw new BadRequestException("Weak password: " + String.join("; ", violations));
        }
    }

    public static List<String> violations(String username, String password) {
        List<String> result = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            result.add("password is required");
            return result;
        }
        if (password.length() < MIN_LENGTH) {
            result.add("at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            result.add("at most " + MAX_BYTES + " bytes in UTF-8");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            result.add("a lowercase letter");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            result.add("an uppercase letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            result.add("a digit");
        }
        if (password.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            result.add("a special character");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            result.add("must not contain the username");
        }
        if (COMMON.contains(lower)) {
            result.add("must not be a commonly used password");
        }
        return result;
    }
}
