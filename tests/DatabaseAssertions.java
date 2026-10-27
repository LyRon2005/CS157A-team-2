package sjsuclassrooms.tests;

import sjsuclassrooms.config.Database;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

/*
 * Java helper called by auth_integration.py, not by normal website requests.
 * Read the test account directly from MySQL to check what was really saved.
 * Check the five ERD columns and database constraints, not only HTTP responses.
 * The database name guard below prevents cleanup from dropping the app database.
 */
public final class DatabaseAssertions {
    public static void main(String[] args) throws Exception {
        Database database = Database.load(Path.of(""));
        if (!database.name().matches("sjsu_auth_test_[a-f0-9]{12}")) throw new IllegalStateException("Refusing non-test database.");
        // The Python test calls this mode in its finally block to remove test data.
        if (args.length > 0 && args[0].equals("drop")) {
            try (Connection c = database.connectServer(); Statement s = c.createStatement()) {
                s.executeUpdate("DROP DATABASE `" + database.name() + "`");
            }
            return;
        }
        try (Connection c = database.connect(); Statement s = c.createStatement()) {
            try (var rs = s.executeQuery("SELECT userID, password_hash, sjsu_email FROM Users WHERE userID='001234567'")) {
                if (!rs.next() || !rs.getString(1).equals("001234567") || !rs.getString(2).startsWith("pbkdf2-sha256$600000$")
                        || !rs.getString(3).equals("integration@sjsu.edu")) throw new AssertionError("Persisted account mismatch");
            }
            try (var rs = s.executeQuery("SHOW COLUMNS FROM Users")) {
                int columns = 0;
                while (rs.next()) { columns++; if (rs.getString("Field").equals("userID") && !rs.getString("Type").equals("char(9)")) throw new AssertionError("ID type"); }
                if (columns != 5) throw new AssertionError("Users must match the five ERD attributes");
            }
            // These intentionally bad INSERT statements must fail inside MySQL.
            for (String sql : new String[]{
                    "INSERT INTO Users VALUES ('123','A','B','valid@sjsu.edu','unused')",
                    "INSERT INTO Users VALUES ('987654321','A','B','invalid@example.com','unused')"}) {
                try { s.executeUpdate(sql); throw new AssertionError("Database accepted invalid account"); }
                catch (java.sql.SQLException expected) { if (expected.getErrorCode() != 3819) throw expected; }
            }
        }
        System.out.println("PASS: ERD columns, leading zero, stored hash, normalized email and database CHECK constraints.");
    }
}
