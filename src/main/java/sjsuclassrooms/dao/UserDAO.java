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
	String sql = "SELECT userID, first_name, last_name, sjsu_email, password_hash " + "FROM Users WHERE userID = ?";
        try (Connection c = database.connect(); PreparedStatement s = c.prepareStatement(sql)) {
	s.setString(1,  studentId);;
	try (ResultSet rs = s.executeQuery()) {
		if (!rs.next()) return null;
		User user = new User(rs.getInt("userID"), rs.getString("first_name"), rs.getString("last_name"), rs.getString("sjsu_email"));
		String passwordHash = rs.getString("password_hash");
		return new Credentials(user, passwordHash);
	}
        }
    }
    // Save one account and its password hash. MySQL rejects duplicate IDs or emails.
    // Let the Servlet turn that SQL error into a safe message for the user.
    public void insert(User user, String hash) throws SQLException {
	String sql = "INSERT INTO Users " + "(userID, first_name, last_name, sjsu_email, password_hash) "
	+ "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = database.connect(); PreparedStatement s = c.prepareStatement(sql)) {
	s.setInt(1,  user.userId());
	s.setString(2,  user.firstName());
	s.setString(3,  user.lastName());
	s.setString(4, user.sjsuEmail());
	s.setString(5,  hash);
	s.executeUpdate();
        }
    }
}
