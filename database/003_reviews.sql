CREATE TABLE Reviews(
    Review_ID INT PRIMARY KEY,
    UserID CHAR(9) NOT NULL,
    written_review VARCHAR(1000) NOT NULL,
    date_created DATETIME NOT NULL,

    FOREIGN KEY(UserID)
        REFERENCES Users(UserId)
);

CREATE TABLE BuildingReview(

    elevator_rating INT NOT NULL,
    bathroom_rating INT NOT NULL,
    lounging_rating INT NOT NULL,
    study_spot_rating INT NOT NULL,
    
    FOREIGN KEY(Review_ID)
        REFERENCES Reviews(Review_ID),
    Foreign KEY(Building_ID)
        REFERENCES Building(Building_ID),
);

CREATE TABLE ClassroomReview(
    desk_rating INT NOT NULL,
    comfort_rating INT NOT NULL ,
    lighting_rating INT NOT NULL,
    spaciousness_rating INT NOT NULL 
    technology_rating INT NOT NULL,
    
    FOREIGN KEY(Review_ID)
        REFERENCES Reviews(Review_ID),
    Foreign KEY(Classroom_ID)
        REFERENCES Classrooms(Classroom_ID)
);