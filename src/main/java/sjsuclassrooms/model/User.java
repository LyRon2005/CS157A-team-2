package sjsuclassrooms.model;

// Holds the user ID, name, and email. It does not hold a password.
// Java creates the constructor and getters for this record.

public record User(int userId, String firstName, String lastName, String sjsuEmail) {}
