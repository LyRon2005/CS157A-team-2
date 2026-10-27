package sjsuclassrooms.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sjsuclassrooms.config.Database;
import sjsuclassrooms.dao.ReviewDAO;
import sjsuclassrooms.model.BuildingReview;
import sjsuclassrooms.model.User;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

// This API handles building reviews. Classroom reviews are not connected yet.
@WebServlet("/api/reviews")
public final class ReviewServlet extends HttpServlet {
    private ReviewDAO reviewDAO;

    @Override
    public void init() throws ServletException {
        try {
            reviewDAO = new ReviewDAO(Database.forServer());
        } catch (Exception error) {
            throw new ServletException("Could not connect to the database.", error);
        }
    }

    // Show reviews for the selected building. Reading does not require login.
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        try {
            int buildingId = readId(request.getParameter("buildingId"));
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.parseInt(request.getParameter("offset"));
            }
            if (offset < 0 || offset > 100000) {
                throw new IllegalArgumentException("Invalid page.");
            }

            String sort = request.getParameter("sort");
            if (sort == null) {
                sort = "newest";
            }
            if (!sort.equals("newest") && !sort.equals("oldest")) {
                throw new IllegalArgumentException("Invalid sort order.");
            }
            if (!reviewDAO.buildingExists(buildingId)) {
                Http.error(response, 404, "Building not found.");
                return;
            }

            User user = UserSession.current(request);
            String viewerId = null;
            if (user != null) {
                viewerId = user.userId();
            }
            List<ReviewDAO.Entry> reviews = reviewDAO.list(
                    buildingId, viewerId, offset, sort.equals("oldest")
            );

            StringJoiner json = new StringJoiner(",");
            int visibleCount = Math.min(20, reviews.size());
            for (int index = 0; index < visibleCount; index++) {
                ReviewDAO.Entry review = reviews.get(index);
                json.add("{\"id\":" + review.id()
                        + ",\"author\":" + Http.quote(review.author())
                        + ",\"text\":" + Http.quote(review.text())
                        + ",\"created\":" + Http.quote(review.created())
                        + ",\"mine\":" + review.mine() + "}");
            }

            boolean hasMore = reviews.size() > 20;
            Http.json(response, 200, "{\"reviews\":[" + json + "],\"hasMore\":" + hasMore + "}");
        } catch (IllegalArgumentException error) {
            Http.error(response, 400, error.getMessage());
        } catch (SQLException error) {
            databaseError(response, error);
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        User user = UserSession.current(request);
        if (user == null) {
            Http.error(response, 401, "Sign in to write a review.");
            return;
        }

        // Accept changes only from our page with the user's session token.
        int port = request.getServerPort();
        String origin = request.getScheme() + "://" + request.getServerName();
        boolean defaultPort = (request.isSecure() && port == 443)
                || (!request.isSecure() && port == 80);
        if (!defaultPort) {
            origin += ":" + port;
        }
        if (!UserSession.validToken(request)
                || !origin.equals(request.getHeader("Origin"))
                || !"SJSUClassrooms".equals(request.getHeader("X-Requested-With"))) {
            Http.error(response, 403, "Refresh the page and try again.");
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null || !contentType.split(";")[0].trim()
                .equalsIgnoreCase("application/x-www-form-urlencoded")) {
            Http.error(response, 415, "Expected a form submission.");
            return;
        }

        try {
            Map<String, String> form = Http.form(request);
            Set<String> allowedFields = Set.of(
                    "buildingId", "buildingName", "writtenReview", "action", "reviewId"
            );
            if (!allowedFields.containsAll(form.keySet())) {
                throw new IllegalArgumentException("Unexpected form field.");
            }

            String action = form.getOrDefault("action", "create");
            if (!action.equals("create") && !action.equals("update") && !action.equals("delete")) {
                throw new IllegalArgumentException("Invalid action.");
            }

            int buildingId;
            if (form.containsKey("buildingId")) {
                buildingId = readId(form.get("buildingId"));
            } else {
                // Keep support for the original form that sent a building name.
                buildingId = reviewDAO.findBuildingId(form.get("buildingName"));
            }
            if (buildingId < 1 || !reviewDAO.buildingExists(buildingId)) {
                Http.error(response, 404, "Building not found.");
                return;
            }

            int reviewId = 0;
            if (!action.equals("create")) {
                reviewId = readId(form.get("reviewId"));
            }

            if (action.equals("delete")) {
                boolean deleted = reviewDAO.delete(reviewId, buildingId, user.userId());
                if (!deleted) {
                    Http.error(response, 404, "Your review was not found.");
                    return;
                }
            } else {
                String writtenReview = form.getOrDefault("writtenReview", "").strip();
                validateText(writtenReview);

                if (action.equals("create")) {
                    // Zero is a placeholder. MySQL gives the new review its real ID.
                    BuildingReview review = new BuildingReview(
                            0,
                            user.userId(),
                            writtenReview,
                            LocalDateTime.now(ZoneOffset.UTC),
                            buildingId
                    );
                    reviewId = reviewDAO.insertBuildingReview(review);
                } else {
                    boolean updated = reviewDAO.update(
                            reviewId, buildingId, user.userId(), writtenReview
                    );
                    if (!updated) {
                        Http.error(response, 404, "Your review was not found.");
                        return;
                    }
                }
            }

            int status = 200;
            if (action.equals("create")) {
                status = 201;
            }
            Http.json(response, status,
                    "{\"reviewId\":" + reviewId + ",\"message\":\"Review saved.\"}");
        } catch (IllegalArgumentException error) {
            Http.error(response, 400, error.getMessage());
        } catch (SQLException error) {
            databaseError(response, error);
        }
    }

    private int readId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException("Choose a valid building or review.");
        }
        return Integer.parseInt(value);
    }

    private void validateText(String text) {
        boolean onlySpaces = text.codePoints().allMatch(
                character -> Character.isWhitespace(character) || Character.isSpaceChar(character)
        );
        if (text.isBlank() || onlySpaces) {
            throw new IllegalArgumentException("Write a review before submitting.");
        }
        if (text.length() > 1000) {
            throw new IllegalArgumentException("Use 1,000 characters or fewer.");
        }
        boolean hiddenCharacters = text.codePoints().anyMatch(
                character -> Character.isISOControl(character)
                        && character != '\n' && character != '\r' && character != '\t'
        );
        if (hiddenCharacters) {
            throw new IllegalArgumentException("Remove hidden control characters.");
        }
    }

    private void databaseError(HttpServletResponse response, SQLException error) throws IOException {
        getServletContext().log("Review database error: " + error.getSQLState());
        Http.error(response, 503, "Reviews are temporarily unavailable. Please try again later.");
    }
}
