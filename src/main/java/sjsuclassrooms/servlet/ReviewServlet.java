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
import java.util.Map;

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

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        User user = UserSession.current(request);

        if (user == null) {
            Http.error(response, 401, "You must be signed in to write a review.");
            return;
        }

        if (!UserSession.validToken(request)) {
            Http.error(response, 403, "Invalid security token.");
            return;
        }

        String contentType = request.getContentType();

        if (contentType == null ||
                !contentType.split(";")[0].trim()
                        .equalsIgnoreCase("application/x-www-form-urlencoded")) {
            Http.error(response, 415, "Expected a form submission.");
            return;
        }

        try {
            Map<String, String> form = Http.form(request);

            String buildingName = form.get("buildingName");
            String writtenReview = form.get("writtenReview");

            if (buildingName == null || buildingName.isBlank()) {
                Http.error(response, 400, "Building name is required.");
                return;
            }

            if (writtenReview == null || writtenReview.trim().isEmpty()) {
                Http.error(response, 400, "Review text is required.");
                return;
            }

            writtenReview = writtenReview.trim();

            if (writtenReview.length() > 1000) {
                Http.error(response, 400, "Review must be 1000 characters or fewer.");
                return;
            }

            int buildingId = reviewDAO.findBuildingId(buildingName);

            if (buildingId == -1) {
                Http.error(response, 404, "Building was not found.");
                return;
            }

            int reviewId = reviewDAO.nextReviewId();

            BuildingReview review = new BuildingReview(
                    reviewId,
                    user.studentId(),
                    writtenReview,
                    LocalDateTime.now(ZoneOffset.UTC),
                    buildingId
            );

            reviewDAO.insertBuildingReview(review);

            Http.json(
                    response,
                    201,
                    "{\"reviewId\":" + reviewId
                            + ",\"message\":\"Review submitted successfully.\"}"
            );

        } catch (IllegalArgumentException error) {
            Http.error(response, 400, error.getMessage());
        } catch (SQLException error) {
            getServletContext().log("Review database error.", error);
            Http.error(response, 503, "Reviews are temporarily unavailable.");
        }
    }
}