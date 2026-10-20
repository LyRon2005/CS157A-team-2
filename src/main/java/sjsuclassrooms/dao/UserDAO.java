package sjsuclassrooms.dao;

// Reads and saves user accounts in MySQL.
// AuthService calls these methods after checking the form values.

import sjsuclassrooms.config.Database;
import sjsuclassrooms.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class UserDAO {
    // Internal login result. Never send passwordHash to the browser.
    public record Credentials(User user, String passwordHash) {}
    private final Database database;
    // Receive the connection helper from the service setup.
    public UserDAO(Database database) { this.database = database; }
    // Find one account by its string ID. Return null if no account exists.
    // Keeping the ID as text preserves leading zeros.
    public Credentials find(String studentId) throws SQLException {
        try (Connection c = database.connect(); PreparedStatement s = c.prepareStatement(
                "SELECT UserId, First_Name, Last_Name, Sjsu_Email, Password FROM Users WHERE UserId = ?")) {
            s.setString(1, studentId);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return null;
                return new Credentials(new User(rs.getString("UserId"), rs.getString("First_Name"),
                        rs.getString("Last_Name"), rs.getString("Sjsu_Email")), rs.getString("Password"));
            }
        }
    }
    // Save one account and its password hash. MySQL rejects duplicate IDs or emails.
    // Let the Servlet turn that SQL error into a safe message for the user.
    public void insert(User user, String hash) throws SQLException {
        try (Connection c = database.connect(); PreparedStatement s = c.prepareStatement(
                "INSERT INTO Users (UserId, First_Name, Last_Name, Sjsu_Email, Password) VALUES (?, ?, ?, ?, ?)")) {
            s.setString(1, user.studentId()); s.setString(2, user.firstName());
            s.setString(3, user.lastName()); s.setString(4, user.email()); s.setString(5, hash);
            s.executeUpdate();
        }
    }
}
