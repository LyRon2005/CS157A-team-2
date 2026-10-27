package sjsuclassrooms.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import sjsuclassrooms.config.Database;
import sjsuclassrooms.dao.ClassroomDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.StringJoiner;

// Read-only API for the map UI. Writing classroom reviews remains a future feature.
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
            var rooms = classrooms.listDuncanRooms();
            if (idText == null) {
                StringJoiner json = new StringJoiner(",");
                for (var room : rooms) json.add(roomJson(room));
                Http.json(response, 200, "{\"buildingId\":20,\"rooms\":[" + json + "]}");
                return;
            }
            if (!idText.matches("[1-9][0-9]{0,8}")) {
                Http.error(response, 400, "Invalid classroom ID.");
                return;
            }
            int id = Integer.parseInt(idText);
            var room = rooms.stream().filter(value -> value.id() == id).findFirst();
            if (room.isEmpty()) {
                Http.error(response, 404, "Duncan Hall classroom not found.");
                return;
            }
            StringJoiner reviews = new StringJoiner(",");
            for (var review : classrooms.reviews(id)) {
                StringJoiner ratings = new StringJoiner(",");
                for (var rating : review.ratings().entrySet()) {
                    ratings.add(Http.quote(rating.getKey()) + ":" + rating.getValue());
                }
                reviews.add("{\"author\":" + Http.quote(review.author())
                        + ",\"text\":" + Http.quote(review.text())
                        + ",\"created\":" + Http.quote(review.created())
                        + ",\"ratings\":{" + ratings + "}}");
            }
            Http.json(response, 200, "{\"room\":" + roomJson(room.get())
                    + ",\"reviews\":[" + reviews + "],\"reviewLimit\":100}");
        } catch (SQLException error) {
            getServletContext().log("Classroom query failed: " + error.getSQLState());
            Http.error(response, 503, "Classroom data is unavailable. Please try again.");
        }
    }

    private String roomJson(ClassroomDAO.Room room) {
        StringJoiner features = new StringJoiner(",");
        for (var feature : room.features()) {
            features.add("{\"name\":" + Http.quote(feature.name())
                    + ",\"quantity\":" + feature.quantity() + "}");
        }
        return "{\"id\":" + room.id() + ",\"number\":" + Http.quote(room.number())
                + ",\"capacity\":" + room.capacity()
                + ",\"type\":" + (room.type() == null ? "null" : Http.quote(room.type()))
                + ",\"demo\":" + room.demo() + ",\"features\":[" + features + "]}";
    }
}
