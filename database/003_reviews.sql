CREATE TABLE IF NOT EXISTS Reviews (
    reviewID INT AUTO_INCREMENT PRIMARY KEY,
    userID CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    written_review VARCHAR(1000) NOT NULL,
    date_created DATETIME NOT NULL,

    FOREIGN KEY (userID)
        REFERENCES Users(userID)
);

CREATE TABLE IF NOT EXISTS BuildingReviews (
    reviewID INT PRIMARY KEY,
    buildingId INT NOT NULL,

    FOREIGN KEY (reviewID)
        REFERENCES Reviews(reviewID),

    FOREIGN KEY (buildingId)
        REFERENCES Building(buildingId)
);

CREATE TABLE IF NOT EXISTS ClassroomReviews (
    reviewID INT PRIMARY KEY,
    classroomId INT NOT NULL,

    FOREIGN KEY (reviewID)
        REFERENCES Reviews(reviewID),

    FOREIGN KEY (classroomId)
        REFERENCES Classrooms(classroomId)
);