CREATE TABLE IF NOT EXISTS Building (
    buildingId INT PRIMARY KEY,
    building_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS Classrooms (
    classroomId INT PRIMARY KEY,
    classroom_num VARCHAR(20) NOT NULL,
    capacity INT,
    classroom_type VARCHAR(100),
    buildingId INT NOT NULL,

    FOREIGN KEY (buildingId)
        REFERENCES Building(buildingId)
);