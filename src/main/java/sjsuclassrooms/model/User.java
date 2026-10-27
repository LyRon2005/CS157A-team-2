package sjsuclassrooms.model;

// Student IDs are text so all nine digits, including leading zeros, are kept.
public record User(String userId, String firstName, String lastName, String sjsuEmail) {}
