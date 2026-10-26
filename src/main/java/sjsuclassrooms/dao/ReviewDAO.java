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

public final class ReviewDAO {
    private final Database database;

    public ReviewDAO(Database database) {
        this.database = database;
    }

    // Finds the next available review ID.
    public int nextReviewId() throws SQLException {
    String sql = "SELECT MAX(Review_Id) AS max_id FROM Reviews";

    try (Connection connection = database.connect();
         PreparedStatement statement = connection.prepareStatement(sql);
         ResultSet result = statement.executeQuery()) {

        result.next();

        int maxId = result.getInt("max_id");

        if (result.wasNull()) {
            return 1;
        }
            return maxId + 1;
        }
    }

    // Inserts a review connected to a classroom.
    public void insertClassroomReview(ClassroomReview review) throws SQLException {
        String reviewSql = """
                INSERT INTO Reviews
                    (Review_Id, UserId, written_review, date_created)
                VALUES (?, ?, ?, ?)
                """;

        String classroomSql = """
                INSERT INTO ClassroomReview
                    (Review_Id, Classroom_id)
                VALUES (?, ?)
                """;

        insertReviewAndTarget(
                review,
                review.getClassroomId(),
                reviewSql,
                classroomSql
        );
    }

    // Inserts a review connected to a building.
    public void insertBuildingReview(BuildingReview review) throws SQLException {
        String reviewSql = """
                INSERT INTO Reviews
                    (Review_Id, UserId, written_review, date_created)
                VALUES (?, ?, ?, ?)
                """;

        String buildingSql = """
                INSERT INTO BuildingReview
                    (Review_Id, Building_ID)
                VALUES (?, ?)
                """;

        insertReviewAndTarget(
                review,
                review.getBuildingId(),
                reviewSql,
                buildingSql
        );
    }

    // Inserts into the parent Reviews table and the appropriate child table.
    private void insertReviewAndTarget(
            Review review,
            int targetId,
            String reviewSql,
            String targetSql
    ) throws SQLException {
        try (Connection connection = database.connect()) {
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement reviewStatement =
                             connection.prepareStatement(reviewSql)) {

                    reviewStatement.setInt(1, review.getReviewId());
                    reviewStatement.setString(2, review.getUserId());
                    reviewStatement.setString(3, review.getWrittenReview());
                    reviewStatement.setTimestamp(
                            4,
                            Timestamp.valueOf(review.getDateCreated())
                    );

                    reviewStatement.executeUpdate();
                }

                try (PreparedStatement targetStatement =
                             connection.prepareStatement(targetSql)) {

                    targetStatement.setInt(1, review.getReviewId());
                    targetStatement.setInt(2, targetId);

                    targetStatement.executeUpdate();
                }

                connection.commit();

            } catch (SQLException error) {
                connection.rollback();
                throw error;
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