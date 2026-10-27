package sjsuclassrooms.config;

import java.sql.*;

// Rename the older project schema without dropping tables or copying account data.
public final class SchemaAlignment {
    private SchemaAlignment() {}

    public static void migrate(Connection connection) throws SQLException {
        // Do not guess how numeric IDs in a different database should be converted.
        String userType = columnType(connection, "Users", "userID");
        if (userType != null && !userType.equals("char") && !userType.equals("varchar")) {
            throw new SQLException("Users.userID must be text to preserve student IDs. Use the project schema; do not import the team dump over existing accounts.");
        }
        renameTable(connection, "BuildingReview", "BuildingReviews");
        renameTable(connection, "ClassroomReview", "ClassroomReviews");
        String[][] changes = {
            {"Users", "UserId", "userID"}, {"Users", "First_Name", "first_name"},
            {"Users", "Last_Name", "last_name"}, {"Users", "Sjsu_Email", "sjsu_email"},
            {"Users", "Password", "password_hash"},
            {"Building", "Building_ID", "buildingId"}, {"Building", "Building_name", "building_name"},
            {"Classrooms", "Classroom_id", "classroomId"}, {"Classrooms", "Building_ID", "buildingId"},
            {"Classrooms", "Classroom_Num", "classroom_num"}, {"Classrooms", "Capacity", "capacity"},
            {"Classrooms", "Classroom_Type", "classroom_type"},
            {"Reviews", "Review_Id", "reviewID"}, {"Reviews", "UserId", "userID"},
            {"Reviews", "wifi_Rating", "wifi_rating"}, {"Reviews", "Cleanliness_Rating", "cleanliness_rating"},
            {"BuildingReviews", "Review_Id", "reviewID"}, {"BuildingReviews", "Building_ID", "buildingId"},
            {"BuildingReviews", "lounging_rating", "lounging_area_rating"},
            {"ClassroomReviews", "Review_Id", "reviewID"}, {"ClassroomReviews", "Classroom_id", "classroomId"},
            {"ClassroomReviews", "comfort_rating", "seating_comfort_rating"},
            {"Features", "FeatureId", "featureId"}, {"Features", "Feature_Name", "feature_name"},
            {"ClassroomFeatures", "Classroom_id", "classroomId"}, {"ClassroomFeatures", "FeatureId", "featureId"},
            {"ClassroomFeatures", "Quantity", "quantity"}, {"BuildingFeatures", "Building_ID", "buildingId"},
            {"BuildingFeatures", "FeatureId", "featureId"}, {"BuildingFeatures", "Quantity", "quantity"}
        };
        for (String[] change : changes) renameColumn(connection, change[0], change[1], change[2]);
        if (tableExists(connection, "Features") && columnName(connection, "Features", "feature_scope") == null) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE Features ADD COLUMN feature_scope VARCHAR(20) NOT NULL DEFAULT 'unspecified'");
                statement.executeUpdate("""
                        UPDATE Features SET feature_scope = 'classroom'
                        WHERE featureId BETWEEN 20001 AND 20006
                          AND feature_name IN ('Projector','Projection screen','Whiteboard','Power outlets','Desks','Chairs')
                          AND feature_scope = 'unspecified'
                        """);
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        String sql = "SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, connection.getCatalog());
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static String columnName(Connection connection, String table, String column) throws SQLException {
        return columnProperty(connection, table, column, "COLUMN_NAME");
    }

    private static String columnType(Connection connection, String table, String column) throws SQLException {
        return columnProperty(connection, table, column, "DATA_TYPE");
    }

    private static String columnProperty(Connection connection, String table, String column, String property) throws SQLException {
        String sql = "SELECT " + property + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, connection.getCatalog());
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void renameTable(Connection connection, String oldName, String newName) throws SQLException {
        if (!tableExists(connection, oldName)) return;
        if (tableExists(connection, newName)) {
            throw new SQLException("Both " + oldName + " and " + newName + " exist. Resolve the duplicate tables before setup.");
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("RENAME TABLE " + oldName + " TO " + newName);
        }
    }

    private static void renameColumn(Connection connection, String table, String oldName, String newName) throws SQLException {
        String actual = columnName(connection, table, oldName);
        if (actual == null || actual.equals(newName)) return;
        String target = columnName(connection, table, newName);
        if (target != null && !actual.equalsIgnoreCase(target)) {
            throw new SQLException("Both old and new columns exist in " + table + ". Resolve them before setup.");
        }
        try (Statement statement = connection.createStatement()) {
            // MySQL does not rename a column while a CHECK refers to that column.
            java.util.Map<String, String> checks = new java.util.LinkedHashMap<>();
            // SHOW CREATE returns executable expressions; CHECK_CLAUSE may escape quotes.
            try (var result = statement.executeQuery("SHOW CREATE TABLE " + table)) {
                result.next();
                for (String line : result.getString(2).split("\\n")) {
                    var match = java.util.regex.Pattern.compile("^\\s*CONSTRAINT `([^`]+)` CHECK \\((.*)\\),?$").matcher(line);
                    if (match.matches() && match.group(2).contains("`" + actual + "`")) checks.put(match.group(1), match.group(2));
                }
            }
            for (String name : checks.keySet()) statement.executeUpdate("ALTER TABLE " + table + " DROP CHECK `" + name.replace("`", "``") + "`");
            boolean renamed = false;
            try {
                statement.executeUpdate("ALTER TABLE " + table + " RENAME COLUMN " + actual + " TO " + newName);
                renamed = true;
            } finally {
                for (var check : checks.entrySet()) {
                    String clause = renamed ? check.getValue().replace("`" + actual + "`", "`" + newName + "`") : check.getValue();
                    statement.executeUpdate("ALTER TABLE " + table + " ADD CONSTRAINT `" + check.getKey().replace("`", "``") + "` CHECK (" + clause + ")");
                }
            }
        }
    }
}
