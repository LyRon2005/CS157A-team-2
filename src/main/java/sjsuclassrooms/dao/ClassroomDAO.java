package sjsuclassrooms.dao;

import sjsuclassrooms.config.Database;
import java.sql.*;
import java.util.*;

public final class ClassroomDAO {
    private final Database database;

    public ClassroomDAO(Database database) {
        this.database = database;
    }

    public record Feature(String name, int quantity) {}
    public record Room(int id, String number, Integer capacity, String type, boolean demo, List<Feature> features) {}

    // Located in: every returned room belongs to Duncan Hall (Building_ID = 20).
    public List<Room> listDuncanRooms() throws SQLException {
        String roomSql = """
                SELECT Classroom_id, Classroom_Num, Capacity, Classroom_Type
                FROM Classrooms WHERE Building_ID = 20
                ORDER BY Classroom_Num, Classroom_id
                """;
        String featureSql = """
                SELECT cf.Classroom_id, f.Feature_Name, cf.Quantity
                FROM ClassroomFeatures cf
                JOIN Features f ON f.FeatureId = cf.FeatureId
                JOIN Classrooms c ON c.Classroom_id = cf.Classroom_id
                WHERE c.Building_ID = 20 ORDER BY f.Feature_Name
                """;
        List<Room> rooms = new ArrayList<>();
        Map<Integer, List<Feature>> byRoom = new HashMap<>();
        try (Connection connection = database.connect()) {
            try (PreparedStatement statement = connection.prepareStatement(featureSql);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int id = result.getInt("Classroom_id");
                    byRoom.computeIfAbsent(id, key -> new ArrayList<>()).add(
                            new Feature(result.getString("Feature_Name"), result.getInt("Quantity")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(roomSql);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int id = result.getInt("Classroom_id");
                    String type = result.getString("Classroom_Type");
                    Integer capacity = (Integer) result.getObject("Capacity");
                    rooms.add(new Room(id, result.getString("Classroom_Num"), capacity, type,
                            "Demo classroom - historical room number".equals(type),
                            byRoom.getOrDefault(id, List.of())));
                }
            }
        }
        return rooms;
    }

    public record ReviewEntry(String author, String text, String created, Map<String, Integer> ratings) {}

    public List<ReviewEntry> reviews(int classroomId) throws SQLException {
        String sql = """
                SELECT u.First_Name, r.written_review, r.date_created,
                       r.wifi_Rating, r.Cleanliness_Rating, r.ac_rating,
                       cr.desk_space_rating, cr.comfort_rating, cr.spaciousness_rating,
                       cr.lighting_rating, cr.technology_rating
                FROM ClassroomReview cr
                JOIN Reviews r ON r.Review_Id = cr.Review_Id
                JOIN Users u ON u.UserId = r.UserId
                JOIN Classrooms c ON c.Classroom_id = cr.Classroom_id
                WHERE cr.Classroom_id = ? AND c.Building_ID = 20
                ORDER BY r.date_created DESC, r.Review_Id DESC LIMIT 100
                """;
        List<ReviewEntry> reviews = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, classroomId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Integer> ratings = new LinkedHashMap<>();
                    for (String name : List.of("wifi_Rating", "Cleanliness_Rating", "ac_rating",
                            "desk_space_rating", "comfort_rating", "spaciousness_rating",
                            "lighting_rating", "technology_rating")) {
                        Number value = (Number) result.getObject(name);
                        ratings.put(name, value == null ? null : value.intValue());
                    }
                    reviews.add(new ReviewEntry(result.getString("First_Name"),
                            result.getString("written_review"),
                            result.getTimestamp("date_created").toLocalDateTime() + "Z", ratings));
                }
            }
        }
        return reviews;
    }
}
