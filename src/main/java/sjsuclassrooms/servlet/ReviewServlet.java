package sjsuclassrooms.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sjsuclassrooms.config.Database;
import sjsuclassrooms.dao.ReviewDAO;
import sjsuclassrooms.dao.ReviewRatingDAO;
import sjsuclassrooms.dao.ReviewRatingDAO.Target;
import sjsuclassrooms.model.User;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

// Building and Duncan classroom reviews share validation and ownership checks.
@WebServlet("/api/reviews")
public final class ReviewServlet extends HttpServlet {
    private ReviewDAO reviewDAO;
    private ReviewRatingDAO rated;

    @Override
    public void init() throws ServletException {
        try {
            reviewDAO = new ReviewDAO(Database.forServer());
            rated = new ReviewRatingDAO(Database.forServer());
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
            if (request.getParameter("mine") != null) { personal(request, response); return; }
            Target target = target(request.getParameter("buildingId"), request.getParameter("classroomId"));
            int buildingId = readId(request.getParameter(target.id));
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
            if (!rated.exists(target, buildingId)) {
                HttpSupport.error(response, 404, "Building or classroom not found.");
                return;
            }

            User user = UserSession.current(request);
            String viewerId = null;
            if (user != null) {
                viewerId = user.userId();
            }
            List<ReviewRatingDAO.Entry> reviews = rated.list(
                    target, buildingId, viewerId, offset, sort.equals("oldest")
            );

            StringJoiner json = new StringJoiner(",");
            int visibleCount = Math.min(20, reviews.size());
            for (int index = 0; index < visibleCount; index++) {
                ReviewRatingDAO.Entry review = reviews.get(index);
                json.add("{\"id\":" + review.id()
                        + ",\"author\":" + HttpSupport.quote(review.author())
                        + ",\"text\":" + HttpSupport.quote(review.text())
                        + ",\"created\":" + HttpSupport.quote(review.created())
                        + ",\"mine\":" + review.mine() + ",\"ratings\":" + numbers(review.ratings()) + "}");
            }

            boolean hasMore = reviews.size() > 20;
            var summary = rated.summary(target, buildingId);
            HttpSupport.json(response, 200, "{\"reviews\":[" + json + "],\"hasMore\":" + hasMore
                    + ",\"summary\":{\"count\":" + summary.count() + ",\"averages\":" + numbers(summary.averages())
                    + ",\"counts\":" + numbers(summary.counts()) + "}}");
        } catch (IllegalArgumentException error) {
            HttpSupport.error(response, 400, error.getMessage());
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
            HttpSupport.error(response, 401, "Sign in to write a review.");
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
            HttpSupport.error(response, 403, "Refresh the page and try again.");
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null || !contentType.split(";")[0].trim()
                .equalsIgnoreCase("application/x-www-form-urlencoded")) {
            HttpSupport.error(response, 415, "Expected a form submission.");
            return;
        }

        try {
            Map<String, String> form = HttpSupport.form(request, 16384);
            Target target = target(form.get("buildingId") != null ? form.get("buildingId") : form.get("buildingName"), form.get("classroomId"));
            if (form.containsKey("buildingId") && form.containsKey("buildingName")) throw new IllegalArgumentException("Choose one target.");
            var allowedFields = new java.util.HashSet<>(Set.of("buildingId", "buildingName", "classroomId", "writtenReview", "action", "reviewId"));
            allowedFields.addAll(target.keys());
            if (!allowedFields.containsAll(form.keySet())) throw new IllegalArgumentException("Unexpected form field.");
            var ratings = new java.util.LinkedHashMap<String,Integer>();
            for (String key : target.keys()) if (form.containsKey(key)) {
                String value = form.get(key);
                if (!value.isEmpty() && !value.matches("[1-5]")) throw new IllegalArgumentException("Ratings must be whole numbers from 1 to 5, or left blank.");
                ratings.put(key, value.isEmpty() ? null : Integer.valueOf(value));
            }

            String action = form.getOrDefault("action", "create");
            if (!action.equals("create") && !action.equals("update") && !action.equals("delete")) {
                throw new IllegalArgumentException("Invalid action.");
            }

            int buildingId;
            if (form.containsKey(target.id)) {
                buildingId = readId(form.get(target.id));
            } else {
                // Keep support for the original form that sent a building name.
                buildingId = reviewDAO.findBuildingId(form.get("buildingName"));
            }
            if (buildingId < 1 || !rated.exists(target, buildingId)) {
                HttpSupport.error(response, 404, "Building or classroom not found.");
                return;
            }

            int reviewId = 0;
            if (!action.equals("create")) {
                reviewId = readId(form.get("reviewId"));
            }

            String writtenReview = form.getOrDefault("writtenReview", "").strip();
            if (!action.equals("delete")) validateText(writtenReview);
            reviewId = rated.save(target, buildingId, user.userId(), reviewId, writtenReview, ratings, action.equals("delete"));
            if (reviewId < 0) { HttpSupport.error(response, 404, "Your review was not found."); return; }

            int status = 200;
            if (action.equals("create")) {
                status = 201;
            }
            HttpSupport.json(response, status,
                    "{\"reviewId\":" + reviewId + ",\"message\":\"Review saved.\"}");
        } catch (IllegalArgumentException error) {
            HttpSupport.error(response, 400, error.getMessage());
        } catch (SQLException error) {
            databaseError(response, error);
        }
    }

    private void personal(HttpServletRequest request,HttpServletResponse response) throws SQLException,IOException {
        User user=UserSession.current(request);
        if(user==null){HttpSupport.error(response,401,"Sign in to view your reviews.");return;}
        var allowed=Set.of("mine","type","offset","sort","reviewId");
        for(var entry:request.getParameterMap().entrySet())
            if(!allowed.contains(entry.getKey())||entry.getValue().length!=1)throw new IllegalArgumentException("Invalid review filter.");
        if(!"1".equals(request.getParameter("mine")))throw new IllegalArgumentException("Invalid review filter.");
        String type=request.getParameter("type");if(type==null)type="all";
        if(!Set.of("all","building","classroom").contains(type))throw new IllegalArgumentException("Invalid review type.");
        String sort=request.getParameter("sort");if(sort==null)sort="newest";
        if(!Set.of("newest","oldest").contains(sort))throw new IllegalArgumentException("Invalid sort order.");
        int offset=request.getParameter("offset")==null?0:Integer.parseInt(request.getParameter("offset"));
        if(offset<0||offset>100000)throw new IllegalArgumentException("Invalid page.");
        Integer id=request.getParameter("reviewId")==null?null:readId(request.getParameter("reviewId"));
        var rows=rated.personal(user.userId(),type,offset,sort.equals("oldest"),id);
        if(id!=null&&rows.isEmpty()){HttpSupport.error(response,404,"Your review was not found.");return;}
        var json=new StringJoiner(",");
        for(var row:rows.subList(0,Math.min(20,rows.size()))) {
            var review=row.review();
            json.add("{\"id\":"+review.id()+",\"kind\":"+HttpSupport.quote(row.kind())+",\"targetId\":"+row.targetId()
                    +",\"targetName\":"+HttpSupport.quote(row.targetName())+",\"editable\":"+row.editable()
                    +",\"text\":"+HttpSupport.quote(review.text())+",\"created\":"+HttpSupport.quote(review.created())
                    +",\"ratings\":"+numbers(review.ratings())+"}");
        }
        HttpSupport.json(response,200,"{\"reviews\":["+json+"],\"hasMore\":"+(rows.size()>20)+"}");
    }

    private Target target(String building, String classroom) {
        if ((building == null) == (classroom == null)) throw new IllegalArgumentException("Choose exactly one building or classroom.");
        return classroom == null ? Target.BUILDING : Target.CLASSROOM;
    }
    private String numbers(Map<String,? extends Number> values) {
        var json = new StringJoiner(",", "{", "}");
        values.forEach((key,value) -> json.add(HttpSupport.quote(key) + ":" + (value == null ? "null" : value.toString())));
        return json.toString();
    }

    private int readId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,8}")) {
            throw new IllegalArgumentException("Choose a valid building, classroom or review.");
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
        HttpSupport.error(response, 503, "Reviews are temporarily unavailable. Please try again later.");
    }
}
