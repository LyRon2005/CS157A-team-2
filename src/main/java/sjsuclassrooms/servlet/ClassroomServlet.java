package sjsuclassrooms.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import sjsuclassrooms.config.Database;
import sjsuclassrooms.dao.ClassroomDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.StringJoiner;

// Room directory API. Reviews are written through ReviewServlet.
@WebServlet("/api/classrooms")
public final class ClassroomServlet extends HttpServlet {
    private ClassroomDAO classrooms;

    @Override
    public void init() throws ServletException {
        try {
            classrooms = new ClassroomDAO(Database.forServer());
        } catch (Exception error) {
            throw new ServletException("Classroom configuration failed.", error);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String idText = request.getParameter("id");
            int buildingId = request.getParameter("buildingId") == null ? 20 : Integer.parseInt(request.getParameter("buildingId"));
            Integer floor = request.getParameter("floor") == null ? null : Integer.valueOf(request.getParameter("floor"));
            if (buildingId < 1 || (floor != null && (floor < -32768 || floor > 32767))) throw new IllegalArgumentException("Invalid room filter.");
            var rooms = classrooms.listRooms(buildingId, floor);
            if (idText == null) {
                StringJoiner json = new StringJoiner(",");
                for (var room : rooms) json.add(roomJson(room));
                HttpSupport.json(response, 200, "{\"buildingId\":" + buildingId + ",\"rooms\":[" + json + "]}");
                return;
            }
            if (!idText.matches("[1-9][0-9]{0,8}")) {
                HttpSupport.error(response, 400, "Invalid classroom ID.");
                return;
            }
            int id = Integer.parseInt(idText);
            var room = rooms.stream().filter(value -> value.id() == id).findFirst();
            if (room.isEmpty()) {
                HttpSupport.error(response, 404, "Duncan Hall classroom not found.");
                return;
            }
            StringJoiner reviews = new StringJoiner(",");
            for (var review : classrooms.reviews(id)) {
                StringJoiner ratings = new StringJoiner(",");
                for (var rating : review.ratings().entrySet()) {
                    ratings.add(HttpSupport.quote(rating.getKey()) + ":" + rating.getValue());
                }
                reviews.add("{\"author\":" + HttpSupport.quote(review.author())
                        + ",\"text\":" + HttpSupport.quote(review.text())
                        + ",\"created\":" + HttpSupport.quote(review.created())
                        + ",\"ratings\":{" + ratings + "}}");
            }
            HttpSupport.json(response, 200, "{\"room\":" + roomJson(room.get())
                    + ",\"reviews\":[" + reviews + "],\"reviewLimit\":100}");
        } catch (IllegalArgumentException error) {
            HttpSupport.error(response, 400, "Invalid room filter.");
        } catch (SQLException error) {
            getServletContext().log("Classroom query failed: " + error.getSQLState());
            HttpSupport.error(response, 503, "Classroom data is unavailable. Please try again.");
        }
    }

    private String roomJson(ClassroomDAO.Room room) {
        StringJoiner features = new StringJoiner(",");
        for (var feature : room.features()) {
            features.add("{\"name\":" + HttpSupport.quote(feature.name())
                    + ",\"quantity\":" + feature.quantity() + "}");
        }
        return "{\"id\":" + room.id() + ",\"number\":" + HttpSupport.quote(room.number())
                + ",\"floor\":" + room.floor() + ",\"capacity\":" + room.capacity()
                + ",\"type\":" + (room.type() == null ? "null" : HttpSupport.quote(room.type()))
                + ",\"demo\":" + room.demo() + ",\"features\":[" + features + "]}";
    }
}
