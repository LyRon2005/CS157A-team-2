package sjsuclassrooms.servlet;

// Receives signup, login, and logout requests from the browser.
// AuthService checks account rules. UserSession manages the current login.

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sjsuclassrooms.config.Database;
import sjsuclassrooms.dao.UserDAO;
import sjsuclassrooms.model.User;
import sjsuclassrooms.service.AuthService;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AuthServlet extends HttpServlet {
    private AuthService service;
    private record Attempts(long until, int count) {}
    private final Map<String, Attempts> attempts = new HashMap<>();

    // Tomcat calls init once when the application starts. Load settings and build the service.
    @Override public void init() throws ServletException {
        try { service = new AuthService(new UserDAO(Database.forServer())); }
        catch (Exception e) { throw new ServletException("Check SJSU_CONFIG or -Dsjsu.config and Connector/J.", e); }
    }

    // Allow at most 30 login/signup attempts per IP in 15 minutes.
    // synchronized protects the shared map when requests arrive together.
    // This local limit resets when Tomcat restarts; it is not shared across servers.
    private synchronized boolean rateLimited(String address) {
        long now = System.currentTimeMillis();
        attempts.values().removeIf(a -> a.until() < now);
        Attempts old = attempts.getOrDefault(address, new Attempts(now + 900_000, 0));
        if (old.count() >= 30 || attempts.size() > 10_000) return true;
        attempts.put(address, new Attempts(old.until(), old.count() + 1));
        return false;
    }

    // Route the request and check its HTTP method before reading account input.
    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo();
        if (path == null || !Set.of("/me", "/signup", "/login", "/logout").contains(path)) {
            Http.error(response, 404, "API route not found."); return;
        }
        String method = path.equals("/me") ? "GET" : "POST";
        if (!method.equals(request.getMethod())) {
            response.setHeader("Allow", method); Http.error(response, 405, "Method not allowed."); return;
        }
        // GET /me reads the existing session. It does not create an anonymous session.
        if (method.equals("GET")) { respond(request, response, 200); return; }
        // Require a same-origin request and the custom header used by auth.js.
        // This local setup uses the direct Tomcat address, not a reverse proxy.
        int port = request.getServerPort();
        String origin = request.getScheme() + "://" + request.getServerName()
                + ((request.isSecure() && port == 443 || !request.isSecure() && port == 80) ? "" : ":" + port);
        if (!origin.equals(request.getHeader("Origin"))
                || !"SJSUClassrooms".equals(request.getHeader("X-Requested-With"))) {
            Http.error(response, 403, "Open the form from this website and try again."); return;
        }
        // The CSRF token is a random session value. Require it before ending a login.
        if (path.equals("/logout")) {
            if (request.getSession(false) != null && !UserSession.validToken(request)) {
                Http.error(response, 403, "Refresh the page and try again."); return;
            }
            UserSession.end(request);
            respond(request, response, 200); return;
        }

        if (rateLimited(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "900");
            Http.error(response, 429, "Too many attempts. Try again in 15 minutes."); return;
        }
        String type = request.getContentType();
        if (type == null || !type.split(";")[0].trim().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            Http.error(response, 415, "Expected a form submission."); return;
        }
        try {
            Map<String, String> form = Http.form(request);
            boolean signup = path.equals("/signup");
            User user = signup ? service.signUp(form) : service.signIn(form);
            if (user == null) { Http.error(response, 401, "Student ID or password is incorrect."); return; }
            UserSession.start(request, user);
            respond(request, response, signup ? 201 : 200);
        // Return safe field errors. Do not return SQL statements, passwords or database settings.
        } catch (AuthService.ValidationException e) {
            Http.json(response, 400, "{\"error\":" + Http.quote(e.getMessage()) + ",\"field\":" + Http.quote(e.field) + "}");
        } catch (IllegalArgumentException e) {
            Http.error(response, 400, e.getMessage());
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) Http.error(response, 409, "That student ID or email is already registered. Please sign in.");
            else {
                getServletContext().log("Database unavailable: SQLState " + e.getSQLState());
                Http.error(response, 503, "Accounts are temporarily unavailable. Please try again later.");
            }
        }
    }

    // Send only public account data and the session CSRF token. Never send a password hash.
    private void respond(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        User user = UserSession.current(request);
        if (user == null) { Http.json(response, status, "{\"user\":null}"); return; }
        Http.json(response, status, "{\"user\":{\"studentId\":" + Http.quote(String.valueOf(user.userId()))
                + ",\"firstName\":" + Http.quote(user.firstName()) + ",\"lastName\":" + Http.quote(user.lastName())
                + ",\"email\":" + Http.quote(user.sjsuEmail()) + "},\"csrfToken\":"
                + Http.quote(UserSession.token(request)) + "}");
    }
}
