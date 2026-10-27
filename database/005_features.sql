-- ERD: Features and the two many-to-many relationships with quantity.
-- Located in is represented by Classrooms.buildingId, defined in 002_locations.sql.
CREATE TABLE IF NOT EXISTS Features (
    featureId INT PRIMARY KEY,
    feature_name VARCHAR(100) NOT NULL UNIQUE,
    feature_scope VARCHAR(20) NOT NULL
);
CREATE TABLE IF NOT EXISTS ClassroomFeatures (
    classroomId INT NOT NULL,
    featureId INT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (classroomId, featureId),
    FOREIGN KEY (classroomId) REFERENCES Classrooms(classroomId),
    FOREIGN KEY (featureId) REFERENCES Features(featureId)
);
CREATE TABLE IF NOT EXISTS BuildingFeatures (
    buildingId INT NOT NULL,
    featureId INT NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (buildingId, featureId),
    FOREIGN KEY (buildingId) REFERENCES Building(buildingId),
    FOREIGN KEY (featureId) REFERENCES Features(featureId)
);
