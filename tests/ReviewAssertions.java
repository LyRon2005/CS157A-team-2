package sjsuclassrooms.tests;

import sjsuclassrooms.config.*;
import sjsuclassrooms.dao.ReviewDAO;
import sjsuclassrooms.model.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.sql.*;

// Exercises the old schema upgrade and transaction rollback in a disposable database.
public final class ReviewAssertions {
    public static void main(String[] args) throws Exception {
        Database db=Database.load(Path.of(""));
        if(!db.name().matches("sjsu_auth_test_[a-f0-9]{12}")) throw new IllegalStateException("Test database only.");
        try(var c=db.connect();var s=c.createStatement()) {
            s.execute("SET FOREIGN_KEY_CHECKS=0");
            try{s.executeUpdate("ALTER TABLE Reviews MODIFY reviewID INT NOT NULL");}
            finally{s.execute("SET FOREIGN_KEY_CHECKS=1");}
            s.executeUpdate("INSERT INTO Reviews (reviewID,userID,written_review,date_created) VALUES (500,'001234567','Legacy review',CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO BuildingReviews (reviewID,buildingId) VALUES (500,22)");
        }
        CreateTables.main(new String[0]);
        ReviewDAO dao=new ReviewDAO(db);
        int id=dao.insertBuildingReview(new BuildingReview(0,"001234567","After migration",LocalDateTime.now(),22));
        if(id<=500)throw new AssertionError("ID sequence did not preserve legacy rows");
        try(var c=db.connect();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT written_review FROM Reviews JOIN BuildingReviews USING(reviewID) WHERE reviewID=500")) {
                if(!r.next()||!r.getString(1).equals("Legacy review"))throw new AssertionError("Legacy review lost");
            }
            int before;
            try(var r=s.executeQuery("SELECT COUNT(*) FROM Reviews")){r.next();before=r.getInt(1);}
            try{dao.insertBuildingReview(new BuildingReview(0,"001234567","Rollback",LocalDateTime.now(),999999));throw new AssertionError("Invalid target accepted");}
            catch(SQLException expected){if(!"23000".equals(expected.getSQLState()))throw expected;}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM Reviews")){r.next();if(r.getInt(1)!=before)throw new AssertionError("Orphan review after rollback");}
            s.executeUpdate("INSERT INTO Classrooms VALUES (9001,'Test',20,'Test',22)");
        }
        int classroomId=dao.insertClassroomReview(new ClassroomReview(0,"001234567","Classroom DAO",LocalDateTime.now(),9001));
        if(dao.delete(classroomId,22,"001234567"))throw new AssertionError("Building delete touched classroom review");
        int duncanReview = dao.insertClassroomReview(new ClassroomReview(
                0,"001234567","Duncan classroom test",LocalDateTime.now(),200010));
        try (var connection = db.connect(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE ClassroomReviews SET seating_comfort_rating=4 WHERE reviewID=" + duncanReview);
            for (int invalid : new int[]{0,6}) {
                try {
                    statement.executeUpdate("UPDATE ClassroomReviews SET seating_comfort_rating=" + invalid + " WHERE reviewID=" + duncanReview);
                    throw new AssertionError("Invalid rating accepted");
                } catch (SQLException expected) {
                    if (expected.getErrorCode()!=3819) throw expected;
                }
            }
            try {
                statement.executeUpdate("UPDATE ClassroomFeatures SET quantity=0 WHERE featureId=20001");
                throw new AssertionError("Zero feature quantity accepted");
            } catch (SQLException expected) {
                if (expected.getErrorCode()!=3819) throw expected;
            }
            try {
                statement.executeUpdate("INSERT INTO ClassroomFeatures VALUES (999999,20001,1)");
                throw new AssertionError("Orphan feature accepted");
            } catch (SQLException expected) {
                if (!"23000".equals(expected.getSQLState())) throw expected;
            }
            statement.executeUpdate("UPDATE ClassroomFeatures SET quantity=99 WHERE featureId=20001");
        }
        CreateTables.main(new String[0]);
        try (var connection = db.connect(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM ClassroomFeatures WHERE featureId=20001 AND quantity<>99")) {
            result.next();
            if(result.getInt(1)!=0) throw new AssertionError("Setup overwrote edited feature quantities");
        }
        System.out.println("PASS: legacy migration preserves relationships, generated IDs, transaction rollback and classroom DAO compatibility.");
    }
}
