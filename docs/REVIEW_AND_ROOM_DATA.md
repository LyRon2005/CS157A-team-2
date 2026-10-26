# Building reviews and indoor directory

## Request flow

Select a map building, confirm, then read its reviews. The panel offers Change building, Write a review, and Explore floors & rooms. Reviews are text-only, matching the existing review model. There are no invented star ratings.

`review.js` calls `ReviewServlet`, which checks the session, CSRF token, input and ownership. `ReviewDAO` uses prepared statements. MySQL assigns review IDs. Inserting a review and its building link is one transaction. Only the author can update or delete their review. Public responses expose first names, not student IDs or email addresses.

Run `sjsuclassrooms.config.CreateTables` from the project directory before deploying. It creates Users, Building, Classrooms and review tables in dependency order. Existing rows are kept. Older Reviews tables are upgraded to AUTO_INCREMENT. The SQL file runner supports our simple statements only, not stored procedures or semicolons inside strings.

## Files to extend

- `src/main/webapp/js/app.js`: map selection, focus and overview.
- `src/main/webapp/js/review.js`: building review list and editor.
- `src/main/webapp/js/building-browser.js`: floor selection, room list, plan pins and classroom review preview.
- `src/main/webapp/data/building-directory.js`: map IDs mapped to database building IDs, source links, floor images and room labels.
- `src/main/webapp/css/explorer.css`: selected building and indoor layouts.
- `src/main/java/sjsuclassrooms/servlet/ReviewServlet.java`: building review API.
- `src/main/java/sjsuclassrooms/dao/ReviewDAO.java`: database operations, including the teammate's classroom insert method.

Classroom reviews remain disabled UI only. The room numbers in the directory are display labels, NOT Classrooms.Classroom_id database keys. When the classroom backend is ready, return actual classroom IDs from its API and connect those IDs to the preview. Do not send a room label as a database ID. No availability or reservation API is simulated.

## Research and limits

- Historical plans and room rosters: [SJSU Campus Utilization Report](https://www.sjsu.edu/fdo/docs/SJSU_Campus_Utilization_Report.pdf). The report uses Fall 2011 rosters; individual drawings have their own revision dates. Rooms may have changed use, numbering or layout. A historical room is not necessarily an available teaching classroom today.
- Engineering maps: [Fall 2024 Engineering Buildings guide](https://www.sjsu.edu/engineering/docs/FA24%20Engineering%20Buildings.pdf), pages 1–4. Room rosters still use the historical report and are labeled accordingly.
- King Library: [official floor directory](https://booking.sjlibrary.org/maps). Source links open the library's current directory; no proprietary map is copied.
- Interior photographs: [SJSU Facility Development Plan 2017](https://www.sjsu.edu/campusmasterplan/docs/SJSU_Facility_Development_Plan_2017.pdf), page 66. Engineering is explicitly an upper-level lobby, not the main entrance. Clark is a lobby photograph without a verified floor. Other buildings show a labeled exterior fallback or a missing-photo state. No unrelated interior is presented as a main lobby.
- ISB: [SJSU confirms eight stories](https://events.sjsu.edu/isb). The old active scene rendered it at 0.5 m. The new height is 45.1104 m (148 ft), an approximate planning envelope from the [2018 environmental document, page 14](https://www.sjsu.edu/fdo/docs/ISB_Final%20MND.pdf#page=14), NOT a verified as-built survey. The footprint is still the public map footprint; this is not an architectural digital twin.

Missing floors and pins are not generated. Pins have percentage coordinates on the full published image and are included only where manually checked. The old equal-height 3D floor bands and placeholder floor buttons have been removed.

## Verification

Run `python tests/review_integration.py "E:/apache-tomcat-11.0.25"` with the local Java/MySQL setup. It creates and removes a guarded temporary test database. It checks public reads, input validation, CSRF, author ownership, edit/delete, concurrent generated IDs, pagination, sorting, repeat setup, migration from the old ID schema, rollback and classroom DAO compatibility. `auth_integration.py` covers the existing account flow.

To inspect the isolated review test UI, set REVIEW_UI_TEST=1. The test prints a local URL and a finish-ui path. Create that file when done so the server and disposable database are cleaned up.

## Teaching-only exploration and image coverage

`building-directory.js` has an explicit `teaching` flag. Photo presence, historical floor availability and the map's broad `academic` color category do not grant exploration. The current allowlist covers Art, BBC, Business Tower, Central Classroom, Clark, Dudley Moorhead, Duncan, Dwight Bentel, Engineering, Health, Hugh Gillis, Industrial Studies, ISB, MacQuarrie, Music, Science, Spartan Complex, Sweeney, Washington Square and Yoshihiro Uchida. Sources: SJSU Building Access and Kinesiology Buildings and Laboratory Spaces. Library, housing, administration, parking and service buildings retain reviews but do not open the indoor browser. This is a UI product rule, not a claim about physical access permission.

All 57 map features have a database review target, including 12 explicitly unnamed ancillary structures (IDs 46–57); these labels are not invented official building names. Existing review IDs and rows are preserved.

There are 35 verified building photographs and two official garage location diagrams. The other 20 map features (eight named buildings and 12 unnamed structures) use their own mapped outline with an explicit photo-unverified label. These outlines are not photographs or architectural renderings. Image load failures also show the outline. Image sources, authors and licenses are attached individually in building-photos.js. Do not replace missing photos with another building's image.
