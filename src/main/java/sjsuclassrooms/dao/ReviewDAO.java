package sjsuclassrooms.dao;

import sjsuclassrooms.config.Database;
import sjsuclassrooms.model.BuildingReview;
import sjsuclassrooms.model.ClassroomReview;
import sjsuclassrooms.model.Review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class ReviewDAO {
    private final Database database;

    public ReviewDAO(Database database) {
        this.database = database;
    }

    // Inserts a review connected to a classroom.
    public int insertClassroomReview(ClassroomReview review) throws SQLException {
        String reviewSql = """
                INSERT INTO Reviews
                    (UserId, written_review, date_created)
                VALUES (?, ?, ?)
                """;

        String classroomSql = """
                INSERT INTO ClassroomReview
                    (Review_Id, Classroom_id)
                VALUES (?, ?)
                """;

        return insertReviewAndTarget(
                review,
                review.getClassroomId(),
                reviewSql,
                classroomSql
        );
    }

    // Inserts a review connected to a building.
    public int insertBuildingReview(BuildingReview review) throws SQLException {
        String reviewSql = """
                INSERT INTO Reviews
                    (UserId, written_review, date_created)
                VALUES (?, ?, ?)
                """;

        String buildingSql = """
                INSERT INTO BuildingReview
                    (Review_Id, Building_ID)
                VALUES (?, ?)
                """;

        return insertReviewAndTarget(
                review,
                review.getBuildingId(),
                reviewSql,
                buildingSql
        );
    }

    // Inserts into the parent Reviews table and the appropriate child table.
    private int insertReviewAndTarget(
            Review review,
            int targetId,
            String reviewSql,
            String targetSql
    ) throws SQLException {
        try (Connection connection = database.connect()) {
            try {
                connection.setAutoCommit(false);
                int generatedId;

                try (PreparedStatement reviewStatement =
                             connection.prepareStatement(reviewSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

                    reviewStatement.setString(1, review.getUserId());
                    reviewStatement.setString(2, review.getWrittenReview());
                    reviewStatement.setTimestamp(
                            3,
                            Timestamp.valueOf(review.getDateCreated())
                    );

                    reviewStatement.executeUpdate();
                    // MySQL creates the ID. Use it again in the child table.
                    try (ResultSet keys = reviewStatement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No review ID returned.");
                        }
                        generatedId = keys.getInt(1);
                    }
                }

                try (PreparedStatement targetStatement =
                             connection.prepareStatement(targetSql)) {

                    targetStatement.setInt(1, generatedId);
                    targetStatement.setInt(2, targetId);

                    targetStatement.executeUpdate();
                }

                connection.commit();
                return generatedId;

            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public record Entry(int id, String author, String text, String created, boolean mine) {}


    // Read 20 reviews and one extra row to check if another page exists.
    public List<Entry> list(int buildingId, String viewer, int offset, boolean oldest) throws SQLException {
        String sql = """
                SELECT r.Review_Id, r.UserId, u.First_Name, r.written_review, r.date_created
                FROM Reviews r
                JOIN BuildingReview b ON b.Review_Id = r.Review_Id
                JOIN Users u ON u.UserId = r.UserId
                WHERE b.Building_ID = ?
                """;
        if (oldest) {
            sql += " ORDER BY r.Review_Id ASC";
        } else {
            sql += " ORDER BY r.Review_Id DESC";
        }
        sql += " LIMIT 21 OFFSET ?";

        List<Entry> entries = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, buildingId);
            statement.setInt(2, offset);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new Entry(
                            result.getInt("Review_Id"),
                            result.getString("First_Name"),
                            result.getString("written_review"),
                            result.getTimestamp("date_created").toLocalDateTime().toString() + "Z",
                            result.getString("UserId").equals(viewer)
                    ));
                }
            }
        }
        return entries;
    }

    // Only the author can change this building review.
    public boolean update(int reviewId, int buildingId, String userId, String text) throws SQLException {
        String sql = """
                UPDATE Reviews r
                JOIN BuildingReview b ON b.Review_Id = r.Review_Id
                SET r.written_review = ?
                WHERE r.Review_Id = ? AND r.UserId = ? AND b.Building_ID = ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, text);
            statement.setInt(2, reviewId);
            statement.setString(3, userId);
            statement.setInt(4, buildingId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean delete(int reviewId, int buildingId, String userId) throws SQLException {
        String findSql = """
                SELECT r.Review_Id
                FROM Reviews r
                JOIN BuildingReview b ON b.Review_Id = r.Review_Id
                WHERE r.Review_Id = ? AND r.UserId = ? AND b.Building_ID = ?
                FOR UPDATE
                """;
        String deleteBuildingSql = "DELETE FROM BuildingReview WHERE Review_Id = ?";
        String deleteReviewSql = "DELETE FROM Reviews WHERE Review_Id = ?";

        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                // Check the author and lock the row until this transaction ends.
                try (PreparedStatement statement = connection.prepareStatement(findSql)) {
                    statement.setInt(1, reviewId);
                    statement.setString(2, userId);
                    statement.setInt(3, buildingId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                    }
                }

                // Delete the child row first because it refers to the parent row.
                try (PreparedStatement statement = connection.prepareStatement(deleteBuildingSql)) {
                    statement.setInt(1, reviewId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(deleteReviewSql)) {
                    statement.setInt(1, reviewId);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException error) {
                connection.rollback();
                throw error;
            }
        }
    }

    public boolean buildingExists(int buildingId) throws SQLException {
        String sql = """
                SELECT Building_ID
                FROM Building
                WHERE Building_ID = ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, buildingId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public int findBuildingId(String buildingName) throws SQLException {
        String sql = """
                SELECT Building_ID
                FROM Building
                WHERE Building_name = ?
                """;

        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, buildingName);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return -1;
                }
                return result.getInt("Building_ID");
            }
        }
    }
}
