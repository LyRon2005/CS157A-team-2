package sjsuclassrooms.config;

// Opens MySQL connections for setup and for the website.
// Read connection settings from the local config file or environment variables.

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class Database {
    private final Properties settings;
    private Database(Properties settings) { this.settings = settings; }
    // Use this path for the setup tool and tests. root is the project folder.
    public static Database load(Path root) throws Exception {
        return fromFile(root.resolve("config/local.properties"));
    }
    // Tomcat may start in another folder. Give it an absolute config file path.
    // Example VM argument: -Dsjsu.config="C:/your-project/config/local.properties"
    // SJSU_CONFIG is an alternative environment variable. Without a file, use DB_* variables.
    public static Database forServer() throws Exception {
        String file = System.getProperty("sjsu.config", System.getenv("SJSU_CONFIG"));
        return fromFile(file == null ? null : Path.of(file));
    }
    // Load Connector/J and read settings. A missing requested file stops setup with an error.
    private static Database fromFile(Path file) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        Properties settings = new Properties();
        if (file != null) try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { settings.load(reader); }
        return new Database(settings);
    }
    // Priority: environment variable, config file, then the default value.
    private String value(String key, String env, String fallback) {
        String override = System.getenv(env);
        return override != null ? override : settings.getProperty(key, fallback);
    }
    // A database name is part of SQL syntax, so allow only a small safe set of characters.
    public String name() {
        String name = value("db.name", "DB_NAME", "SJSUClassrooms");
        if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid database name.");
        return name;
    }
    // Open a connection to the application database. The caller must close it.
    public Connection connect() throws SQLException { return open(true); }
    // Open a connection without selecting a database. Only setup and test cleanup need this.
    public Connection connectServer() throws SQLException { return open(false); }
    // Build the JDBC URL. Timeouts stop a failed database connection from waiting forever.
    // Each call opens a new connection; this small local project does not use a connection pool.
    private Connection open(boolean selectDatabase) throws SQLException {
        String host = value("db.host", "DB_HOST", "localhost"), port = value("db.port", "DB_PORT", "3306");
        if (!host.matches("[a-zA-Z0-9.-]+") || !port.matches("[0-9]{1,5}")) throw new SQLException("Invalid database host or port.");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + (selectDatabase ? name() : "");
        Properties auth = new Properties();
        auth.setProperty("user", value("db.user", "DB_USER", "sjsu_app"));
        auth.setProperty("password", value("db.password", "DB_PASSWORD", ""));
        auth.setProperty("connectionTimeZone", "UTC");
        auth.setProperty("connectTimeout", "5000");
        auth.setProperty("socketTimeout", "10000");
        auth.setProperty("sslMode", value("db.sslMode", "DB_SSL_MODE", "PREFERRED"));
        // Local classroom development. Use VERIFY_IDENTITY with a trusted CA remotely.
        auth.setProperty("allowPublicKeyRetrieval", String.valueOf(host.equals("localhost") || host.equals("127.0.0.1")));
        return DriverManager.getConnection(url, auth);
    }
}
