CREATE TABLE IF NOT EXISTS Reviews (
    Review_Id INT PRIMARY KEY,
    UserId CHAR(9) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    written_review VARCHAR(1000) NOT NULL,
    date_created DATETIME NOT NULL,

    FOREIGN KEY (UserId)
        REFERENCES Users(UserId)
);

CREATE TABLE IF NOT EXISTS BuildingReview (
    Review_Id INT PRIMARY KEY,
    Building_ID INT NOT NULL,

    FOREIGN KEY (Review_Id)
        REFERENCES Reviews(Review_Id),

    FOREIGN KEY (Building_ID)
        REFERENCES Building(Building_ID)
);

CREATE TABLE IF NOT EXISTS ClassroomReview (
    Review_Id INT PRIMARY KEY,
    Classroom_id INT NOT NULL,

    FOREIGN KEY (Review_Id)
        REFERENCES Reviews(Review_Id),

    FOREIGN KEY (Classroom_id)
        REFERENCES Classrooms(Classroom_id)
);