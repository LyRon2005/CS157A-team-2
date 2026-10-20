package sjsuclassrooms.servlet;

// Shared methods for reading forms and sending JSON responses.
// Other Servlets can also use these methods.

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Http {
    private Http() {}
    // Escape quotes, backslashes and control characters before putting a string in JSON.
    public static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            if (ch == '\\' || ch == '"') out.append('\\').append(ch);
            else if (ch < 32) out.append(String.format("\\u%04x", (int) ch));
            else out.append(ch);
        }
        return out.append('"').toString();
    }
    // Send UTF-8 JSON with an HTTP status. Do not cache account responses.
    public static void json(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(body);
    }
    // Keep errors in one JSON shape so the frontend can display them consistently.
    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        json(response, status, "{\"error\":" + quote(message) + "}");
    }
    // Read at most 8193 bytes to detect a form larger than the 8192-byte limit.
    // Reject duplicate keys instead of letting one value silently replace another.
    public static Map<String, String> form(HttpServletRequest x) throws IOException {
        byte[] body = x.getInputStream().readNBytes(8193);
        if (body.length > 8192) throw new IllegalArgumentException("Form is too large.");
        Map<String, String> values = new HashMap<>();
        for (String pair : new String(body, StandardCharsets.ISO_8859_1).split("&", -1)) {
            String[] parts = pair.split("=", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid form.");
            String key = decode(parts[0]);
            String value = decode(parts[1]);
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate form field.");
        }
        return values;
    }
    // Decode form percent escapes and plus signs, then require valid UTF-8.
    // Reject damaged text rather than replacing bytes and saving a different value.
    private static String decode(String encoded) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < encoded.length(); i++) {
            char ch = encoded.charAt(i);
            if (ch == '%') {
                if (i + 2 >= encoded.length()) throw new IllegalArgumentException("Invalid form encoding.");
                int a = Character.digit(encoded.charAt(++i), 16), b = Character.digit(encoded.charAt(++i), 16);
                if (a < 0 || b < 0) throw new IllegalArgumentException("Invalid form encoding.");
                bytes.write((a << 4) | b);
            } else bytes.write(ch == '+' ? ' ' : ch);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("Use valid UTF-8 text."); }
    }
}
