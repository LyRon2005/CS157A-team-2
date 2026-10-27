package sjsuclassrooms.dao;

import sjsuclassrooms.config.Database;
import java.sql.*;
import java.util.*;

// Shared review operations. Table and column names come only from this fixed list.
public final class ReviewRatingDAO {
    public enum Target {
        BUILDING("BuildingReviews", "buildingId", List.of("elevator_rating","bathroom_rating","lounging_area_rating","study_spot_rating")),
        CLASSROOM("ClassroomReviews", "classroomId", List.of("desk_space_rating","seating_comfort_rating","spaciousness_rating","lighting_rating","technology_rating"));
        public final String table, id;
        public final List<String> own;
        Target(String table, String id, List<String> own) { this.table=table; this.id=id; this.own=own; }
        public List<String> keys() { var keys=new ArrayList<>(COMMON); keys.addAll(own); return keys; }
    }
    private static final List<String> COMMON=List.of("cleanliness_rating","wifi_rating","ac_rating");
    private final Database db;
    public ReviewRatingDAO(Database db) { this.db=db; }
    public record Entry(int id,String author,String text,String created,boolean mine,Map<String,Integer> ratings) {}
    public record Summary(int count,Map<String,Double> averages,Map<String,Integer> counts) {}
    private String from(Target t) { return " FROM Reviews r JOIN "+t.table+" t ON t.reviewID=r.reviewID"; }
    private String col(Target t,String key) { return (COMMON.contains(key)?"r.":"t.")+key; }
    public boolean exists(Target t,int id) throws SQLException {
        String sql=t==Target.BUILDING?"SELECT 1 FROM Building WHERE buildingId=?":"SELECT 1 FROM Classrooms WHERE classroomId=? AND buildingId=20";
        try(var c=db.connect();var s=c.prepareStatement(sql)) { s.setInt(1,id);try(var r=s.executeQuery()){return r.next();} }
    }
    public List<Entry> list(Target t,int id,String viewer,int offset,boolean oldest) throws SQLException {
        String sql="SELECT r.reviewID,r.userID,u.first_name,r.written_review,r.date_created";
        for(String key:t.keys()) sql+=","+col(t,key);
        sql+=from(t)+" JOIN Users u ON u.userID=r.userID WHERE t."+t.id+"=? ORDER BY r.date_created "+(oldest?"ASC":"DESC")+", r.reviewID "+(oldest?"ASC":"DESC")+" LIMIT 21 OFFSET ?";
        var entries=new ArrayList<Entry>();
        try(var c=db.connect();var s=c.prepareStatement(sql)) {
            s.setInt(1,id);s.setInt(2,offset);
            try(var r=s.executeQuery()) { while(r.next()) {
                var ratings=new LinkedHashMap<String,Integer>();
                for(String key:t.keys()) { int value=r.getInt(key);ratings.put(key,r.wasNull()?null:value); }
                entries.add(new Entry(r.getInt("reviewID"),r.getString("first_name"),r.getString("written_review"),r.getTimestamp("date_created").toLocalDateTime()+"Z",r.getString("userID").equals(viewer),ratings));
            } }
        }
        return entries;
    }
    public record Personal(Entry review,String kind,int targetId,String targetName,boolean editable) {}

    // The signed-in user ID is supplied by the session, never by a query parameter.
    public List<Personal> personal(String user,String type,int offset,boolean oldest,Integer reviewId) throws SQLException {
        String sql="SELECT r.reviewID,r.userID,u.first_name,r.written_review,r.date_created,"
                + "br.buildingId,cr.classroomId,room.buildingId AS classroomBuilding,"
                + "b.building_name,cb.building_name AS classroomBuildingName,room.classroom_num";
        for(String key:COMMON)sql+=",r."+key;
        for(String key:Target.BUILDING.own)sql+=",br."+key;
        for(String key:Target.CLASSROOM.own)sql+=",cr."+key;
        sql+=" FROM Reviews r JOIN Users u ON u.userID=r.userID "
                + "LEFT JOIN BuildingReviews br ON br.reviewID=r.reviewID LEFT JOIN Building b ON b.buildingId=br.buildingId "
                + "LEFT JOIN ClassroomReviews cr ON cr.reviewID=r.reviewID LEFT JOIN Classrooms room ON room.classroomId=cr.classroomId "
                + "LEFT JOIN Building cb ON cb.buildingId=room.buildingId WHERE r.userID=? "
                + "AND (br.reviewID IS NOT NULL OR cr.reviewID IS NOT NULL)";
        if(type.equals("building"))sql+=" AND br.reviewID IS NOT NULL";
        if(type.equals("classroom"))sql+=" AND cr.reviewID IS NOT NULL";
        if(reviewId!=null)sql+=" AND r.reviewID=?";
        sql+=" ORDER BY r.date_created "+(oldest?"ASC":"DESC")+", r.reviewID "+(oldest?"ASC":"DESC")+" LIMIT 21 OFFSET ?";
        var rows=new ArrayList<Personal>();
        try(var c=db.connect();var statement=c.prepareStatement(sql)) {
            int parameter=1;statement.setString(parameter++,user);
            if(reviewId!=null)statement.setInt(parameter++,reviewId);
            statement.setInt(parameter,offset);
            try(var result=statement.executeQuery()) {while(result.next()) {
                boolean building=result.getObject("buildingId")!=null;
                Target target=building?Target.BUILDING:Target.CLASSROOM;
                var ratings=new LinkedHashMap<String,Integer>();
                for(String key:target.keys()){int value=result.getInt(key);ratings.put(key,result.wasNull()?null:value);}
                Entry review=new Entry(result.getInt("reviewID"),result.getString("first_name"),result.getString("written_review"),
                        result.getTimestamp("date_created").toLocalDateTime()+"Z",true,ratings);
                String name=building?result.getString("building_name"):result.getString("classroomBuildingName")+" · Room "+result.getString("classroom_num");
                rows.add(new Personal(review,building?"building":"classroom",result.getInt(target.id),name,building||result.getInt("classroomBuilding")==20));
            }}
        }
        return rows;
    }

    // Aggregate all reviews, not only the visible page. NULL is excluded from each average.
    public Summary summary(Target t,int id) throws SQLException {
        String sql="SELECT COUNT(*)";
        for(String key:t.keys())sql+=",AVG("+col(t,key)+"),COUNT("+col(t,key)+")";
        sql+=from(t)+" WHERE t."+t.id+"=?";
        try(var c=db.connect();var s=c.prepareStatement(sql)) {
            s.setInt(1,id);try(var r=s.executeQuery()) {
                r.next();var averages=new LinkedHashMap<String,Double>();var counts=new LinkedHashMap<String,Integer>();int i=2;
                for(String key:t.keys()) { double value=r.getDouble(i++);averages.put(key,r.wasNull()?null:value);counts.put(key,r.getInt(i++)); }
                return new Summary(r.getInt(1),averages,counts);
            }
        }
    }
    // A single transaction saves the text, common ratings, and subtype ratings together.
    // Missing rating fields preserve old scores on edit. An empty field clears a score.
    public int save(Target t,int target,String user,int review,String text,Map<String,Integer> ratings,boolean delete) throws SQLException {
        if(!t.keys().containsAll(ratings.keySet()))throw new IllegalArgumentException("Unknown rating.");
        for(Integer score:ratings.values())if(score!=null&&(score<1||score>5))throw new IllegalArgumentException("Use 1 to 5 stars.");
        try(var c=db.connect()) {
            c.setAutoCommit(false);
            try {
                if(review>0) {
                    try(var s=c.prepareStatement("SELECT r.reviewID"+from(t)+" WHERE r.reviewID=? AND r.userID=? AND t."+t.id+"=? FOR UPDATE")) {
                        s.setInt(1,review);s.setString(2,user);s.setInt(3,target);
                        try(var r=s.executeQuery()){if(!r.next()){c.rollback();return -1;}}
                    }
                } else {
                    try(var s=c.prepareStatement("INSERT INTO Reviews(userID,written_review,date_created) VALUES(?,?,UTC_TIMESTAMP())",Statement.RETURN_GENERATED_KEYS)) {
                        s.setString(1,user);s.setString(2,text);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();review=r.getInt(1);}
                    }
                    try(var s=c.prepareStatement("INSERT INTO "+t.table+"(reviewID,"+t.id+") VALUES(?,?)")) {s.setInt(1,review);s.setInt(2,target);s.executeUpdate();}
                }
                if(delete) {
                    for(String table:List.of(t.table,"Reviews"))try(var s=c.prepareStatement("DELETE FROM "+table+" WHERE reviewID=?")){s.setInt(1,review);s.executeUpdate();}
                } else {
                    try(var s=c.prepareStatement("UPDATE Reviews SET written_review=? WHERE reviewID=?")){s.setString(1,text);s.setInt(2,review);s.executeUpdate();}
                    for(var score:ratings.entrySet()) {
                        String table=COMMON.contains(score.getKey())?"Reviews":t.table;
                        try(var s=c.prepareStatement("UPDATE "+table+" SET "+score.getKey()+"=? WHERE reviewID=?")) {
                            if(score.getValue()==null)s.setNull(1,Types.TINYINT);else s.setInt(1,score.getValue());s.setInt(2,review);s.executeUpdate();
                        }
                    }
                }
                c.commit();return review;
            } catch(SQLException|RuntimeException error) {c.rollback();throw error;}
        }
    }
}
