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
            runSqlFile(s, "database/001_users.sql");
            runSqlFile(s, "database/002_locations.sql");
            runSqlFile(s, "database/002_buildingData.sql");
            runSqlFile(s, "database/003_reviews.sql");
            // Older review tables used IDs chosen by Java. Keep rows and let MySQL assign new IDs.
            try (var columns = c.getMetaData().getColumns(database.name(), null, "%", "Review_Id")) {
                boolean upgrade = false;
                while (columns.next()) {
                    if ("Reviews".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                            && !"YES".equals(columns.getString("IS_AUTOINCREMENT"))) upgrade = true;
                }
                if (upgrade) {
                    s.execute("SET FOREIGN_KEY_CHECKS=0");
                    try { s.executeUpdate("ALTER TABLE Reviews MODIFY Review_Id INT NOT NULL AUTO_INCREMENT"); }
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
                statement.executeUpdate(command);
            }
        }
    }
}
