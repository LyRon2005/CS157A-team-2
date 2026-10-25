package sjsuclassrooms.model;

import java.time.LocalDateTime;

public class ClassroomReview extends Review {
    private int classroomId;

    public ClassroomReview(
            int reviewId,
            String userId,
            String writtenReview,
            LocalDateTime dateCreated,
            int classroomId
    ) {
        super(reviewId, userId, writtenReview, dateCreated);
        this.classroomId = classroomId;
    }

    public int getClassroomId() {
        return classroomId;
    }
}