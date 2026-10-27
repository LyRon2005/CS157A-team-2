package sjsuclassrooms.tests;
import sjsuclassrooms.config.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

// Only a disposable database is changed to the old schema during this test.
public final class SchemaAlignmentAssertions {
    private static List<String> snapshot(Connection c) throws Exception {
        List<String> rows = new ArrayList<>();
        for (String table : List.of("Users","Building","Classrooms","Reviews","BuildingReviews","ClassroomReviews","ClassroomFeatures","BuildingFeatures")) {
            try (var s=c.createStatement(); var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1,2")) {
                while(r.next()) {
                    StringBuilder row=new StringBuilder(table);
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.append("|").append(r.getString(i));
                    rows.add(row.toString());
                }
            }
        }
        return rows;
    }
    public static void main(String[] args) throws Exception {
        Database db=Database.load(Path.of(""));
        if(!db.name().matches("sjsu_auth_test_[a-f0-9]{12}")) throw new IllegalStateException("Test database only");
        List<String> before;
        try(var c=db.connect(); var statement=c.createStatement()) {
            before=snapshot(c);
            var rename=SchemaAlignment.class.getDeclaredMethod("renameColumn", Connection.class,String.class,String.class,String.class);
            rename.setAccessible(true);
            rename.invoke(null,c,"Users","userID","UserId");
            rename.invoke(null,c,"Users","first_name","First_Name");
            rename.invoke(null,c,"Users","last_name","Last_Name");
            rename.invoke(null,c,"Users","sjsu_email","Sjsu_Email");
            rename.invoke(null,c,"Users","password_hash","Password");
            rename.invoke(null,c,"Building","buildingId","Building_ID");
            rename.invoke(null,c,"Building","building_name","Building_name");
            rename.invoke(null,c,"Classrooms","classroomId","Classroom_id");
            rename.invoke(null,c,"Classrooms","buildingId","Building_ID");
            rename.invoke(null,c,"Classrooms","classroom_num","Classroom_Num");
            rename.invoke(null,c,"Classrooms","capacity","Capacity");
            rename.invoke(null,c,"Classrooms","classroom_type","Classroom_Type");
            rename.invoke(null,c,"Reviews","reviewID","Review_Id");
            rename.invoke(null,c,"Reviews","userID","UserId");
            rename.invoke(null,c,"Reviews","wifi_rating","wifi_Rating");
            rename.invoke(null,c,"Reviews","cleanliness_rating","Cleanliness_Rating");
            rename.invoke(null,c,"BuildingReviews","reviewID","Review_Id");
            rename.invoke(null,c,"BuildingReviews","buildingId","Building_ID");
            rename.invoke(null,c,"BuildingReviews","lounging_area_rating","lounging_rating");
            rename.invoke(null,c,"ClassroomReviews","reviewID","Review_Id");
            rename.invoke(null,c,"ClassroomReviews","classroomId","Classroom_id");
            rename.invoke(null,c,"ClassroomReviews","seating_comfort_rating","comfort_rating");
            rename.invoke(null,c,"Features","featureId","FeatureId");
            rename.invoke(null,c,"Features","feature_name","Feature_Name");
            rename.invoke(null,c,"ClassroomFeatures","classroomId","Classroom_id");
            rename.invoke(null,c,"ClassroomFeatures","featureId","FeatureId");
            rename.invoke(null,c,"ClassroomFeatures","quantity","Quantity");
            rename.invoke(null,c,"BuildingFeatures","buildingId","Building_ID");
            rename.invoke(null,c,"BuildingFeatures","featureId","FeatureId");
            rename.invoke(null,c,"BuildingFeatures","quantity","Quantity");
            statement.executeUpdate("RENAME TABLE BuildingReviews TO BuildingReview, ClassroomReviews TO ClassroomReview");
            statement.executeUpdate("ALTER TABLE Features DROP COLUMN feature_scope");
        }
        CreateTables.main(new String[0]);
        CreateTables.main(new String[0]);
        try(var c=db.connect(); var s=c.createStatement()) {
            if(!before.equals(snapshot(c))) throw new AssertionError("Migration changed data");
            try(var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
                r.next(); if(r.getInt(1)!=15)throw new AssertionError("Expected 15 tables");
            }
            try(var r=s.executeQuery("SELECT COUNT(*) FROM Features WHERE feature_scope='classroom'")) {
                r.next(); if(r.getInt(1)!=6)throw new AssertionError("Feature scope migration failed");
            }
        }
        System.out.println("PASS: legacy schema renamed, 15 tables, account/review/room data preserved, repeat setup safe.");
    }
}
