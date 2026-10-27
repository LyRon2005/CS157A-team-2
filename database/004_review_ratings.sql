-- ERD rating attributes. NULL means not rated, not zero stars.
-- CreateTables skips columns already present. Run manually only for missing columns.
ALTER TABLE Reviews ADD COLUMN wifi_rating TINYINT NULL CHECK (wifi_rating BETWEEN 1 AND 5);
ALTER TABLE Reviews ADD COLUMN cleanliness_rating TINYINT NULL CHECK (cleanliness_rating BETWEEN 1 AND 5);
ALTER TABLE Reviews ADD COLUMN ac_rating TINYINT NULL CHECK (ac_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReviews ADD COLUMN desk_space_rating TINYINT NULL CHECK (desk_space_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReviews ADD COLUMN seating_comfort_rating TINYINT NULL CHECK (seating_comfort_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReviews ADD COLUMN spaciousness_rating TINYINT NULL CHECK (spaciousness_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReviews ADD COLUMN lighting_rating TINYINT NULL CHECK (lighting_rating BETWEEN 1 AND 5);
ALTER TABLE ClassroomReviews ADD COLUMN technology_rating TINYINT NULL CHECK (technology_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReviews ADD COLUMN elevator_rating TINYINT NULL CHECK (elevator_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReviews ADD COLUMN bathroom_rating TINYINT NULL CHECK (bathroom_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReviews ADD COLUMN lounging_area_rating TINYINT NULL CHECK (lounging_area_rating BETWEEN 1 AND 5);
ALTER TABLE BuildingReviews ADD COLUMN study_spot_rating TINYINT NULL CHECK (study_spot_rating BETWEEN 1 AND 5);
