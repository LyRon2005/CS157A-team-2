package sjsuclassrooms.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import sjsuclassrooms.model.User;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

// Other Servlets can use this class to read the signed-in user and check form tokens.
public final class UserSession {
    private static final String USER = "user";
    private static final String CSRF = "csrf";
    private static final SecureRandom RANDOM = new SecureRandom();

    private UserSession() {}

    // Return null when the visitor is not signed in. Do not create a new session.
    public static User current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute(USER);
    }

    public static String token(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(CSRF);
    }

    // A protected form must send the token from the current login session.
    public static boolean validToken(HttpServletRequest request) {
        String expected = token(request);
        return expected != null && Objects.equals(expected, request.getHeader("X-CSRF-Token"));
    }

    // Create a new session ID after login. Tomcat applies the timeout from web.xml.
    public static void start(HttpServletRequest request, User user) {
        end(request);
        HttpSession session = request.getSession(true);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        session.setAttribute(USER, user);
        session.setAttribute(CSRF, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static void end(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
