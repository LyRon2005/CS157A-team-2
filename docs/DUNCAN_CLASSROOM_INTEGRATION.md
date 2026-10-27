# Duncan Hall classroom integration

This change is on feature/building-reviews-map. It integrates the ClassroomSearch.java
file from main commit eb052d7 without replacing the existing building review UI.

## What changed in the teammate file
- Keep the GET search page, JOIN + LIKE search, and View Reviews page.
- Match ERD/SQL names: classroomId, buildingId, reviewID, ClassroomReviews.
- Use seating_comfort_rating, as named in the ERD.
- Scope search and review lookup to Duncan Hall (buildingId 20).
- Show the author's first name instead of exposing the student ID.
- Add a demo notice. No classroom review POST/write endpoint is added.

## SQL files and relationships
- 004_review_ratings.sql adds the 12 ERD rating attributes to Reviews and its subtypes.
  NULL means not rated. CHECK allows only integer values 1 through 5.
- 005_features.sql creates Features, ClassroomFeatures and BuildingFeatures.
  Relationship tables have a composite primary key, foreign keys and positive quantity.
- Located in is Classrooms.buildingId -> Building.buildingId.
- 006_duncan_demo.sql inserts only Duncan Hall demo classrooms and their features.
- CreateTables.java runs these files after the original SQL files. It skips existing
  ADD COLUMN statements using metadata, so running setup again preserves data.
  The ALTER script alone is not repeatable in Workbench; use CreateTables for reruns.
- Existing reviews retain their IDs and text. New rating fields remain NULL.
  Building rating input is not implemented in this change.

## Demo data
66 historical room numbers are taken from the existing Duncan Hall directory.
Their current classroom use is NOT verified. Capacity stays NULL.
Random seed 20261026 was used once to generate a fixed SQL seed:
46 rooms have all 6 feature types, 17 have 1-4 types, and 3 have none.
Features: projector, projection screen, whiteboard, power outlets, desks and chairs.
All quantities are simulated, including desk/chair counts. They are not room capacity.
No users, reviews, or ratings are fabricated. Existing matching room records are
preserved rather than reclassified as demo data. Seed IDs are reserved and collisions
are skipped; inspect those conflicts if your database already uses these IDs.
BuildingFeatures is ready but has no fabricated building inventory.

## UI and extension points
Duncan Hall -> Explore floors & rooms -> choose floor -> choose room.
ClassroomDAO reads features and classroom reviews.
ClassroomServlet exposes GET /api/classrooms and GET /api/classrooms?id=...
Only Duncan Hall records are returned. Unknown/out-of-scope IDs return 404.
duncan-classrooms.js connects these reads to the existing floor browser.
Other buildings retain their previous behavior.
The classroom review editor stays disabled. This change reads existing reviews only.
Up to 100 recent classroom reviews are shown; pagination can be added later.
The standalone teammate page is /classrooms.

## Verification
tests/review_integration.py uses an isolated temporary MySQL database and Tomcat.
It checks existing building reviews, Duncan search/API, feature distribution,
missing ratings, read-only classroom behavior, foreign keys, rating bounds and
setup reruns that preserve edited quantities. ReviewAssertions.java supplies the
temporary review fixture. No test reviews are inserted into the real database.

## Team schema alignment
Merged team branch feature/erd-database-alignment (7413dcd).
SQL table and column names follow the supplied SJSUClassrooms.sql dump.
SchemaAlignment.java renames old tables/columns before setup without dropping data.
007_remaining_erd.sql adds Administrators, Course, Section, ClassMeeting, Reports,
and Suspension. These tables are ready for future work; their UI is not implemented.
Student IDs and their foreign keys stay CHAR(9), not INT, to keep leading zeros.
Review IDs remain AUTO_INCREMENT. Existing name/email limits and rating checks stay.
The team dump is a naming reference, not a file to import over current accounts.
Unknown existing feature scopes become unspecified; Duncan demo features use classroom.
