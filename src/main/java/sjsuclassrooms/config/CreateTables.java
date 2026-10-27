package sjsuclassrooms.config;

// Run from the project folder. Setup keeps existing accounts and reviews.

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

public final class CreateTables {
    public static void main(String[] args) throws Exception {
        // Read the local MySQL settings. Do not put a password in this source file.
        Database database = Database.load(Path.of(""));
        // Connect to the server first because the application database may not exist yet.
        try (Connection c = database.connectServer(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database.name() + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        // Create parent tables before tables that reference them.
        try (Connection c = database.connect(); Statement s = c.createStatement()) {
            SchemaAlignment.migrate(c);
            runSqlFile(s, "database/001_users.sql");
            runSqlFile(s, "database/002_locations.sql");
            runSqlFile(s, "database/002_buildingData.sql");
            runSqlFile(s, "database/003_reviews.sql");
            runSqlFile(s, "database/004_review_ratings.sql");
            runSqlFile(s, "database/005_features.sql");
            runSqlFile(s, "database/006_duncan_demo.sql");
            runSqlFile(s, "database/007_remaining_erd.sql");
            // Older review tables used IDs chosen by Java. Keep rows and let MySQL assign new IDs.
            try (var columns = c.getMetaData().getColumns(database.name(), null, "%", "reviewID")) {
                boolean upgrade = false;
                while (columns.next()) {
                    if ("Reviews".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && !"YES".equals(columns.getString("IS_AUTOINCREMENT"))) upgrade = true;
                }
                if (upgrade) {
                    s.execute("SET FOREIGN_KEY_CHECKS=0");
                    try { s.executeUpdate("ALTER TABLE Reviews MODIFY reviewID INT NOT NULL AUTO_INCREMENT"); }
                    finally { s.execute("SET FOREIGN_KEY_CHECKS=1"); }
                }
            }
        }
        System.out.println("Database " + database.name() + ": user, location and review tables are ready.");
    }

    // For our simple SQL files only: no procedures or semicolons inside text values.
    private static void runSqlFile(Statement statement, String file) throws Exception {
        String sql = Files.readString(Path.of(file));
        // Remove comment lines before splitting the SQL statements.
        sql = sql.replaceAll("(?m)^[\\t ]*--[^\\r\\n]*", "");
        for (String part : sql.split(";")) {
            String command = part.trim();
            if (!command.isEmpty()) {
                // Our ALTER scripts only add individual columns. Skip an existing column on reruns.
                java.util.regex.Matcher addColumn = java.util.regex.Pattern.compile(
                        "(?is)^ALTER TABLE (\\w+) ADD COLUMN (\\w+)\\s+.*"
                ).matcher(command);
                if (addColumn.matches()) {
                    Connection connection = statement.getConnection();
                    try (var columns = connection.getMetaData().getColumns(
                            connection.getCatalog(), null, addColumn.group(1), addColumn.group(2))) {
                        if (columns.next()) continue;
                    }
                }
                statement.executeUpdate(command);
            }
        }
    }
}
