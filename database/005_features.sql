-- ERD: Features and the two many-to-many relationships with Quantity.
-- Located in is represented by Classrooms.Building_ID, defined in 002_locations.sql.
CREATE TABLE IF NOT EXISTS Features (
    FeatureId INT PRIMARY KEY,
    Feature_Name VARCHAR(100) NOT NULL UNIQUE
);
CREATE TABLE IF NOT EXISTS ClassroomFeatures (
    Classroom_id INT NOT NULL,
    FeatureId INT NOT NULL,
    Quantity INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (Classroom_id, FeatureId),
    FOREIGN KEY (Classroom_id) REFERENCES Classrooms(Classroom_id),
    FOREIGN KEY (FeatureId) REFERENCES Features(FeatureId)
);
CREATE TABLE IF NOT EXISTS BuildingFeatures (
    Building_ID INT NOT NULL,
    FeatureId INT NOT NULL,
    Quantity INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (Building_ID, FeatureId),
    FOREIGN KEY (Building_ID) REFERENCES Building(Building_ID),
    FOREIGN KEY (FeatureId) REFERENCES Features(FeatureId)
);
