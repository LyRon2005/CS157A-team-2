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
    public record Room(int id, String number, Integer capacity, String type, Integer floor, boolean demo, List<Feature> features) {}

    // Located in: every returned room belongs to Duncan Hall (buildingId = 20).
    public List<Room> listDuncanRooms() throws SQLException { return listRooms(20, null); }

    public List<Room> listRooms(int buildingId, Integer floor) throws SQLException {
        String roomSql = """
                SELECT classroomId, classroom_num, capacity, classroom_type, room_floor
                FROM (
                    SELECT c.*, CASE WHEN classroom_num REGEXP '^[0-9]'
                        THEN CAST(LEFT(classroom_num, 1) AS UNSIGNED) ELSE NULL END AS room_floor
                    FROM Classrooms c WHERE buildingId = ?
                ) rooms WHERE (? IS NULL OR room_floor = ?)
                ORDER BY classroom_num, classroomId
                """;
        String featureSql = """
                SELECT cf.classroomId, f.feature_name, cf.quantity
                FROM ClassroomFeatures cf
                JOIN Features f ON f.featureId = cf.featureId
                JOIN Classrooms c ON c.classroomId = cf.classroomId
                WHERE c.buildingId = ? ORDER BY f.feature_name
                """;
        List<Room> rooms = new ArrayList<>();
        Map<Integer, List<Feature>> byRoom = new HashMap<>();
        try (Connection connection = database.connect()) {
            try (PreparedStatement statement = connection.prepareStatement(featureSql)) {
                statement.setInt(1, buildingId);
                try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int id = result.getInt("classroomId");
                    byRoom.computeIfAbsent(id, key -> new ArrayList<>()).add(
                            new Feature(result.getString("feature_name"), result.getInt("quantity")));
                }
            }
            }
            try (PreparedStatement statement = connection.prepareStatement(roomSql)) {
                statement.setInt(1, buildingId);
                if (floor == null) { statement.setNull(2, Types.SMALLINT); statement.setNull(3, Types.SMALLINT); }
                else { statement.setInt(2, floor); statement.setInt(3, floor); }
                try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int id = result.getInt("classroomId");
                    String type = result.getString("classroom_type");
                    Integer capacity = (Integer) result.getObject("capacity");
                    Number roomFloor = (Number) result.getObject("room_floor");
                    rooms.add(new Room(id, result.getString("classroom_num"), capacity, type, roomFloor == null ? null : roomFloor.intValue(),
                            "Demo classroom - historical room number".equals(type),
                            byRoom.getOrDefault(id, List.of())));
                }
            }
        }
        }
        return rooms;
    }

    public record ReviewEntry(String author, String text, String created, Map<String, Integer> ratings) {}

    public List<ReviewEntry> reviews(int classroomId) throws SQLException {
        String sql = """
                SELECT u.first_name, r.written_review, r.date_created,
                       r.wifi_rating, r.cleanliness_rating, r.ac_rating,
                       cr.desk_space_rating, cr.seating_comfort_rating, cr.spaciousness_rating,
                       cr.lighting_rating, cr.technology_rating
                FROM ClassroomReviews cr
                JOIN Reviews r ON r.reviewID = cr.reviewID
                JOIN Users u ON u.userID = r.userID
                JOIN Classrooms c ON c.classroomId = cr.classroomId
                WHERE cr.classroomId = ? AND c.buildingId = 20
                ORDER BY r.date_created DESC, r.reviewID DESC LIMIT 100
                """;
        List<ReviewEntry> reviews = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, classroomId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Integer> ratings = new LinkedHashMap<>();
                    for (String name : List.of("wifi_rating", "cleanliness_rating", "ac_rating",
                            "desk_space_rating", "seating_comfort_rating", "spaciousness_rating",
                            "lighting_rating", "technology_rating")) {
                        Number value = (Number) result.getObject(name);
                        ratings.put(name, value == null ? null : value.intValue());
                    }
                    reviews.add(new ReviewEntry(result.getString("first_name"),
                            result.getString("written_review"),
                            result.getTimestamp("date_created").toLocalDateTime() + "Z", ratings));
                }
            }
        }
        return reviews;
    }
}
