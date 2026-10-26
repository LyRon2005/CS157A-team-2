package sjsuclassrooms.config;

// Run this file once to create the database and the Users table.
// Run it from the project folder. Existing tables and accounts are kept.

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
        // Now select the database and run the Users table script. Resources close automatically.
        try (Connection c = database.connect(); Statement s = c.createStatement()) {
            s.executeUpdate(Files.readString(Path.of("database/001_users.sql")));
            s.executeUpdate(Files.readString(Path.of("database/002_locations.sql")));
            s.executeUpdate(Files.readString(Path.of("database/002_building_data.sql"))); 
            s.executeUpdate(Files.readString(Path.of("database/003_reviews.sql")));
        }
        System.out.println("Database " + database.name() + " and Users table are ready. Existing data was preserved.");
    }
}
