package sjsuclassrooms.service;

// Checks signup and login values, then calls UserDAO.
// The server checks input even when the browser has already checked it.

import java.sql.SQLException;
import sjsuclassrooms.model.User;
import sjsuclassrooms.dao.UserDAO;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;

public final class AuthService {
    private final UserDAO users;
    // For an unknown account, still check a hash to reduce login timing differences.
    private final String dummyHash = PasswordHasher.hash("Timing-only-placeholder-not-an-account");
    public AuthService(UserDAO users) { this.users = users; }
    // Include the field name so the browser can show the error under the correct input.
    public static final class ValidationException extends IllegalArgumentException {
        public final String field;
        public ValidationException(String field, String message) { super(message); this.field = field; }
    }
    private static void invalid(String field, String message) { throw new ValidationException(field, message); }
    // Reject extra fields, such as an invented admin role. Missing values fail their own checks.
    private static void fields(Map<String, String> form, Set<String> expected) {
        if (!expected.containsAll(form.keySet())) throw new IllegalArgumentException("Unexpected form fields. Refresh the page and try again.");
    }
    // Create an account only after every required value passes validation.
    // Email is stored in lowercase. The password is kept exactly as entered before hashing.
    public User signUp(Map<String, String> form) throws SQLException {
        fields(form, Set.of("studentId", "firstName", "lastName", "email", "password", "confirmPassword"));
        String id = studentId(form), password = password(form);
        if (password.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)))
            invalid("password", "Password cannot contain only spaces.");
        String first = name(form, "firstName"), last = name(form, "lastName");
        String rawEmail = form.getOrDefault("email", "");
        if (rawEmail.matches("(?s).*[\\p{Cc}\\p{Cf}].*")) invalid("email", "Email cannot contain hidden or control characters.");
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String local = email.endsWith("@sjsu.edu") ? email.substring(0, email.length() - 9) : "";
        if (local.length() > 64 || !local.matches("[a-z0-9_%+\\-]+(?:\\.[a-z0-9_%+\\-]+)*"))
            invalid("email", "Use a valid SJSU email: 1–64 characters before @sjsu.edu, with no spaces or empty dot segments.");
        if (!password.equals(form.get("confirmPassword"))) invalid("confirmPassword", "Passwords do not match.");
        User user = new User(id, first, last, email);
        users.insert(user, PasswordHasher.hash(password));
        return user;
    }
    // Use the same public error for an unknown ID and a wrong password.
    // Return null for a failed password check; the Servlet returns HTTP 401.
    public User signIn(Map<String, String> form) throws SQLException {
        fields(form, Set.of("studentId", "password"));
        String id = studentId(form), password = password(form);
        UserDAO.Credentials credentials = users.find(id);
        boolean valid = PasswordHasher.verify(password, credentials == null ? dummyHash : credentials.passwordHash());
        return valid && credentials != null ? credentials.user() : null;
    }
    // Accept exactly nine ASCII digits. Do not convert the value to an integer.
    private static String studentId(Map<String, String> form) {
        String value = form.getOrDefault("studentId", "");
        if (!value.matches("[0-9]{9}")) invalid("studentId", "Student ID must contain exactly 9 digits (0–9).");
        return value;
    }
    // Java counts UTF-16 units here, matching JavaScript string.length in the form.
    // Do not trim or shorten passwords, because that changes the user secret.
    private static String password(Map<String, String> form) {
        String value = form.getOrDefault("password", "");
        if (value.length() < 8 || value.length() > 128) invalid("password", "Use a password between 8 and 128 characters.");
        return value; // Passwords must not be trimmed.
    }
    // Accept letters from different languages and common name separators.
    // NFC puts equivalent accented text into one form before it is saved.
    // Reject hidden control characters before cleaning spaces.
    private static String name(Map<String, String> form, String key) {
        String raw = form.getOrDefault(key, "");
        if (raw.matches("(?s).*[\\p{Cc}\\p{Cf}].*")) invalid(key, "Names cannot contain hidden or control characters.");
        String value = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replaceAll("\\p{Zs}", " ").trim().replaceAll(" +", " ");
        if (value.length() > 100 || !value.matches("\\p{L}[\\p{L}\\p{M}]*(?:[ '\\u2019\\-][\\p{L}][\\p{L}\\p{M}]*)*"))
            invalid(key, "Use 1–100 characters: letters (including accents), spaces, hyphens or apostrophes.");
        return value;
    }
}
