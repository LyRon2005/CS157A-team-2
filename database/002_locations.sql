CREATE TABLE IF NOT EXISTS Building (
    Building_ID INT PRIMARY KEY,
    Building_name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS Classrooms (
    Classroom_id INT PRIMARY KEY,
    Classroom_Num VARCHAR(20) NOT NULL,
    Capacity INT,
    Classroom_Type VARCHAR(100),
    Building_ID INT NOT NULL,

    FOREIGN KEY (Building_ID)
        REFERENCES Building(Building_ID)
);