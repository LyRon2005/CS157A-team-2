package sjsuclassrooms.model;

import java.time.LocalDateTime;

public class BuildingReview extends Review {
    private int buildingId;

    public BuildingReview(
            int reviewId,
            String userId,
            String writtenReview,
            LocalDateTime dateCreated,
            int buildingId
    ) {
        super(reviewId, userId, writtenReview, dateCreated);
        this.buildingId = buildingId;
    }

    public int getBuildingId() {
        return buildingId;
    }
}