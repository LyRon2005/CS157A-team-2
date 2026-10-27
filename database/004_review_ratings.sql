-- ERD rating attributes. NULL means not rated, not zero stars.
-- CreateTables skips columns already present. Run manually only for missing columns.
ALTER TABLE Reviews ADD COLUMN wifi_Rating TINYINT NULL CHECK (wifi_Rating BETWEEN 1 AND 5);
ALTER TABLE Reviews ADD COLUMN Cleanliness_Rating TINYINT NULL CHECK (Cleanliness_Rating BETWEEN 1 AND 5);
ALTER TABLE Reviews ADD COLUMN ac_rating TINYINT NULL CHECK (ac_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReview ADD COLUMN desk_space_rating TINYINT NULL CHECK (desk_space_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReview ADD COLUMN comfort_rating TINYINT NULL CHECK (comfort_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReview ADD COLUMN spaciousness_rating TINYINT NULL CHECK (spaciousness_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReview ADD COLUMN lighting_rating TINYINT NULL CHECK (lighting_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReview ADD COLUMN technology_rating TINYINT NULL CHECK (technology_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReview ADD COLUMN elevator_rating TINYINT NULL CHECK (elevator_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReview ADD COLUMN bathroom_rating TINYINT NULL CHECK (bathroom_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReview ADD COLUMN lounging_rating TINYINT NULL CHECK (lounging_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReview ADD COLUMN study_spot_rating TINYINT NULL CHECK (study_spot_rating BETWEEN 1 AND 5);
