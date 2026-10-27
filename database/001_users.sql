-- Database layer: create the Users table from the team ERD.
-- Run with CreateTables.java, or select sjsu_classrooms in Workbench before running this SQL.
-- IF NOT EXISTS keeps an existing table and its accounts. It does not upgrade old columns.
-- userID is text so a value such as 001234567 keeps its leading zeros.
-- The primary key and unique email constraint also protect against simultaneous signups.
-- CHECK rules are a database safety net. AuthService applies stricter input rules.
-- utf8mb4 supports international names. password_hash holds an encoded hash, not the original secret.
-- Users entity from the team ERD. userID is a student ID, not AUTO_INCREMENT.
-- password_hash stores PBKDF2 hash, salt and work factor, never plaintext.
CREATE TABLE IF NOT EXISTS Users (
    userID CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    sjsu_email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (userID),
    CONSTRAINT uq_users_email UNIQUE (sjsu_email),
    CONSTRAINT ck_users_student_id CHECK (userID REGEXP '^[0-9]{9}$'),
    CONSTRAINT ck_users_email CHECK (sjsu_email REGEXP '^[A-Za-z0-9._%+-]+@sjsu[.]edu$'),
    CONSTRAINT ck_users_first_name CHECK (CHAR_LENGTH(TRIM(first_name)) > 0),
    CONSTRAINT ck_users_last_name CHECK (CHAR_LENGTH(TRIM(last_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
