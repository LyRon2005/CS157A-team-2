package sjsuclassrooms.servlet;

import java.io.*;
import java.sql.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import sjsuclassrooms.config.Database;

@WebServlet("/classrooms")
public class ClassroomSearch extends HttpServlet {

    // manages the classroom search requests from users
    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {

        // HTML response
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter outp = response.getWriter();

        // the display for the HTML page
        outp.println("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        outp.println("<title>SJSU Classroom Search</title></head><body>");
        outp.println("<p><a href='" + req.getContextPath() + "/'>Back to home</a></p>");


        // connects to the MySQL database
        try (Connection con = Database.forServer().connect()) {
            String id = req.getParameter("id");

            // this will display the classroom search if there isn't any classroom id given
            if (id == null) {
                searchClassrooms(req, outp, con);
            }
            // displays the reviews for the classrooms if the id is given
            else {
                try {
                    viewReviews(Integer.parseInt(id), outp, con);
                }
                catch (NumberFormatException e) {
                    outp.println("<p>Invalid classroom ID.</p>");
                }
            }
        }

        // manages the database connection and SQL errors
        catch (Exception e) {
            getServletContext().log("Classroom database error", e);
            outp.println("<p>Failed to load the classrooms.</p>");
        }

        outp.println("</body></html>");
    }

    // search classrooms functional requirement
    private void searchClassrooms(HttpServletRequest req, PrintWriter outp, Connection con) throws SQLException {

        // gets the building & classrooms from user
        String building = req.getParameter("building");
        String classroom = req.getParameter("classroom");

        // will use empty strings if there isn't any given building and/or classroom
        if (building == null) {
            building = "";
        }
        if (classroom == null) {
            classroom = "";
        }

        // classroom search display
        outp.println("<h1>Search Duncan Hall Classrooms</h1><p>Demo room records use historical room numbers. Features are simulated, not a current campus inventory.</p>");
        outp.println("<form method='get' action='classrooms'>");
        outp.println("Building: <input name='building' value='" + html(building) + "'> ");
        outp.println("Classroom: <input name='classroom' value='" + html(classroom) + "'> ");
        outp.println("<button type='submit'>Search</button></form><hr>");

        // traverses the database for classrooms that match the given building and classroom from the user
        // uses JOIN to connect the classrooms and building info
        String sql = "SELECT c.classroomId, b.building_name, c.classroom_num, "
                + "c.capacity, c.classroom_type "
                + "FROM Classrooms c JOIN Building b ON c.buildingId = b.buildingId "
                + "WHERE b.buildingId = 20 AND b.building_name LIKE ? AND c.classroom_num LIKE ? "
                + "ORDER BY b.building_name, c.classroom_num LIMIT 100";

        // executes the SQL query
        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setString(1,  "%" + building + "%");
            stmt.setString(2, "%" + classroom + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                outp.println("<table border='1' cellpadding='8'>");
                outp.println("<tr><th>Building</th><th>Room</th><th>Capacity</th>"
                        + "<th>Type</th><th>Reviews</th></tr>");

                int count = 0;

                // goes through all the classrooms that the query returned
                while (rs.next()) {
                    count++;
                    int id = rs.getInt("classroomId");

                    // the classroom query result displayed
                    outp.println("<tr>");
                    outp.println("<td>" + html(rs.getString("building_name")) + "</td>");
                    outp.println("<td>" + html(rs.getString("classroom_num")) + "</td>");
                    outp.println("<td>" + html(rs.getString("capacity")) + "</td>");
                    outp.println("<td>" + html(rs.getString("classroom_type")) + "</td>");
                    outp.println("<td><a href='classrooms?id=" + id + "'>View Reviews</a></td>");
                    outp.println("</tr>");
                }

                outp.println("</table>");

                // for if there weren't any classrooms that were found from the query
                if (count == 0) {
                    outp.println("<p>There was no classrooms found</p>");
                }
            }
        }
    }

    // View Classroom Reviews Functional Requirement
    private void viewReviews(int classroomId, PrintWriter outp, Connection con) throws SQLException {
        outp.println("<p><a href='classrooms'>Back to classroom search</a></p>");

        // retrieves the classroom's review and ratings
        // LEFT JOIN to display the classrooms without any reviews as well
        String sql = "SELECT b.building_name, c.classroom_num, "
                + "r.reviewID, u.first_name AS author, r.written_review, r.date_created, "
                + "r.cleanliness_rating, r.wifi_rating, r.ac_rating, "
                + "cr.desk_space_rating, cr.seating_comfort_rating, "
                + "cr.spaciousness_rating, cr.lighting_rating, cr.technology_rating "
                + "FROM Classrooms c "
                + "JOIN Building b ON b.buildingId = c.buildingId "
                + "LEFT JOIN ClassroomReviews cr ON cr.classroomId = c.classroomId "
                + "LEFT JOIN Reviews r ON r.reviewID = cr.reviewID "
                + "LEFT JOIN Users u ON u.userID = r.userID "
                + "WHERE c.buildingId = 20 AND c.classroomId = ? ORDER BY r.date_created DESC";

        // executes query
        try (PreparedStatement stmt = con.prepareStatement(sql)) {
            stmt.setInt(1,  classroomId);

            try (ResultSet rs = stmt.executeQuery()) {
                boolean classroomFound = false;
                int reviews = 0;

                // traverses through all the classroom's reviews
                while (rs.next()) {
                    if (!classroomFound) {
                        classroomFound = true;

                        outp.println("<h1>" + html(rs.getString("building_name")) + " - Classroom " + html(rs.getString("classroom_num")) + "</h1>");
                        outp.println("<h2>Classroom Reviews</h2>");
                    }

                    // displays the review info if it exists
                    if (rs.getObject("reviewID") != null) {
                        reviews++;

                        // displays the reviewer, date created, written review, and ratings
                        outp.println("<hr>");
                        outp.println("<p><b>Author:</b>" + html(rs.getString("author")) + " | <b>Date:</b> " + html(rs.getString("date_created")) + "</p>");
                        outp.println("<p>" + html(rs.getString("written_review")) + "</p>");
                        outp.println("<p>Cleanliness: " + rating(rs, "cleanliness_rating")
                                + " | Wi-Fi: " + rating(rs, "wifi_rating")
                                + " | AC: " + rating(rs, "ac_rating") + "</p>");

                        outp.println("<p>Lighting: " + rating(rs, "lighting_rating")
                                + " | Seating: " + rating(rs, "seating_comfort_rating")
                                + " | Space: " + rating(rs, "spaciousness_rating") + "</p>");

                        outp.println("<p>Desk space: " + rating(rs, "desk_space_rating")
                                + " | Technology: " + rating(rs, "technology_rating") + "</p>");
                    }
                }

                // for if the classroom or review are missing
                if (!classroomFound) {
                    outp.println("<p>Classroom was not found</p>");
                }
                else if (reviews == 0) {
                    outp.println("<p>There are no reviews for this classroom</p>");
                }
            }
        }

    }

    // Returns rating
    private String rating(ResultSet rs, String col) throws SQLException {
        String val = rs.getString(col);
        // returns this msg if there isn't any rating for the classroom
        if (val == null) {
            return "Classroom isn't rated";
        }
        else {
            return val + "/5";
        }
    }

    private String html(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

}
