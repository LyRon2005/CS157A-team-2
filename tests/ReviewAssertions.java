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
            try{s.executeUpdate("ALTER TABLE Reviews MODIFY Review_Id INT NOT NULL");}
            finally{s.execute("SET FOREIGN_KEY_CHECKS=1");}
            s.executeUpdate("INSERT INTO Reviews VALUES (500,'001234567','Legacy review',CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO BuildingReview VALUES (500,22)");
        }
        CreateTables.main(new String[0]);
        ReviewDAO dao=new ReviewDAO(db);
        int id=dao.insertBuildingReview(new BuildingReview(0,"001234567","After migration",LocalDateTime.now(),22));
        if(id<=500)throw new AssertionError("ID sequence did not preserve legacy rows");
        try(var c=db.connect();var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT written_review FROM Reviews JOIN BuildingReview USING(Review_Id) WHERE Review_Id=500")) {
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
        System.out.println("PASS: legacy migration preserves relationships, generated IDs, transaction rollback and classroom DAO compatibility.");
    }
}
