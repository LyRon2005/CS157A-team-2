-- Database layer: create the Users table from the team ERD.
-- Run with CreateTables.java, or select sjsu_classrooms in Workbench before running this SQL.
-- IF NOT EXISTS keeps an existing table and its accounts. It does not upgrade old columns.
-- UserId is text so a value such as 001234567 keeps its leading zeros.
-- The primary key and unique email constraint also protect against simultaneous signups.
-- CHECK rules are a database safety net. AuthService applies stricter input rules.
-- utf8mb4 supports international names. Password holds an encoded hash, not the original secret.
-- Users entity from the team ERD. UserId is a student ID, not AUTO_INCREMENT.
-- Password stores PBKDF2 hash, salt and work factor, never plaintext.
CREATE TABLE IF NOT EXISTS Users (
    UserId CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    First_Name VARCHAR(100) NOT NULL,
    Last_Name VARCHAR(100) NOT NULL,
    Sjsu_Email VARCHAR(254) NOT NULL,
    Password VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (UserId),
    CONSTRAINT uq_users_email UNIQUE (Sjsu_Email),
    CONSTRAINT ck_users_student_id CHECK (UserId REGEXP '^[0-9]{9}$'),
    CONSTRAINT ck_users_email CHECK (Sjsu_Email REGEXP '^[A-Za-z0-9._%+-]+@sjsu[.]edu$'),
    CONSTRAINT ck_users_first_name CHECK (CHAR_LENGTH(TRIM(First_Name)) > 0),
    CONSTRAINT ck_users_last_name CHECK (CHAR_LENGTH(TRIM(Last_Name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
