package sjsuclassrooms.model;
import java.time.LocalDateTime;

public class Review {
    private int reviewId;
    private String userId;
    private String writtenReview;
    private LocalDateTime dateCreated;

    public Review(int reviewId, String userId, String writtenReview, LocalDateTime dateCreated) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.writtenReview = writtenReview;
        this.dateCreated = dateCreated;
    }

    public int getReviewId() {
        return reviewId;
    }

    public String getUserId() {
        return userId;
    }

    public String getWrittenReview() {
        return writtenReview;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

}
