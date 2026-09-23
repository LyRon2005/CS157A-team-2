# SJSU Spaces — Campus homepage

## Quick start after extracting the ZIP

Install JDK 17 or newer. On Windows, double-click `run-demo.cmd`, then open http://localhost:8080. For another port, run `run-demo.cmd 8081` in a terminal. On macOS/Linux, run `sh run-demo.sh` (or `sh run-demo.sh 8081`). Stop the server with Ctrl+C.

No Maven, Tomcat, MySQL, Python or Internet connection is required to run this prototype. A modern browser with WebGL is required. Links to external source websites still require Internet access. Always extract the ZIP before running it.

## Run in Eclipse

1. Select JDK 17 or newer under **Window → Preferences → Java → Installed JREs**.
2. Choose **File → Import → General → Existing Projects into Workspace** and select `SJSUClassroomsDemo`.
3. Open `src/demo/Main.java` and choose **Run As → Java Application**.
4. Open http://localhost:8080.

The working directory must contain the `web` folder. Set it under **Run Configurations → Main → Arguments → Working directory** if necessary. If port 8080 is occupied, enter `8081` in Program arguments and open http://localhost:8081. Stop the server using Eclipse's Terminate button.

PowerShell commands, from the project folder:

```powershell
javac --release 17 -encoding UTF-8 -d bin src/demo/Main.java
java -cp bin demo.Main
```

## Current map

The homepage shows 57 structures inside the Main Campus boundary, with 2D roads, paths, lawns and mapped trees. The overview looks northeast from Boccardo Gate. Camera movement is controlled by building selection and the overview button.

Select a building on the map, through search, or through a featured shortcut. A confirmation dialog asks whether you would like to book a space there. Cancel preserves the current view; confirm zooms to that building. The floor panel is an exploration preview: room information and booking availability will be added later. The homepage makes no booking API calls.

## Floor data and accuracy

`web/floor-data.js` stores 30 source-backed floor counts (21 from a 2015 assessment) and 10 additional partial or unresolved evidence records with source URLs, reviewed September 22, 2026. Unknown counts remain unavailable. These override OSM level counts in the homepage, including King Library (eight above-ground floors, plus a separate lower level).

Measured floor elevations are unavailable, so the exterior is not divided into floor bands. Unknown exterior heights remain 0.5 m footprint plates. No heights are invented to make the model look complete.

SJSU maintains detailed room and floor-plan records in MetaBIM, with access administered by FD&O: https://www.engineering.sjsu.edu/fdo/services/real-estate/physical_planning.php . These restricted records were not accessed.

To update a floor record, use the building's stable ID from `web/main-scene.js`, set `count`, `source`, and `note` in `web/floor-data.js`; set `variableHeight: true` when a uniform stack would misrepresent the building. Verify new data before adding it. Sources include older housing brochures, so this is not a certified current-condition survey.

The full 94-footprint archive is retained separately. See `/sources.html` for source geometry, methodology, coverage and downloads. Source data is not a verified inventory of every SJSU property.

## Legacy booking API

The Java backend retains its local demonstration API and TSV persistence for later integration; it is not exposed by this homepage. Its simulated rooms do not reserve space at SJSU.

## Rebuild map data

Python with `shapely` and `pyproj` is required only for rebuilding data, not for running the Java demo:

```powershell
python sources/build_dataset.py
python sources/build_main_scene.py
python sources/validate_dataset.py
```

These scripts read downloaded snapshots in `sources/`. Geometry checks cover polygon validity, unique IDs, positive dimensions, registry coverage, height provenance and sample-room scope.

## Architecture and limits

This Java Application uses the JDK HTTP server; it is not yet the Servlet/JSP/Tomcat/MySQL application described in the project specification. It listens only on 127.0.0.1. Authentication, administrative approval and booking cancellation are not implemented. TSV storage is intended for one local demo process.

Campus data and MapLibre GL JS 5.6.2 are bundled locally. The map requires WebGL but makes no external requests to render. See `THIRD_PARTY_NOTICES.md` and `web/vendor/LICENSE-MapLibre.txt` for attribution and licenses.

## Sources

- Official SJSU map: https://map.sjsu.edu/
- Printable maps: https://www.sjsu.edu/printmaps/
- OpenStreetMap attribution and license: https://www.openstreetmap.org/copyright
- City GIS: https://geo.sanjoseca.gov/server/rest/services/DOT/DOT_Basemap_InfrastructureMaintenance/MapServer/13
- Gate location: https://www.sjsu.edu/campusmasterplan/docs/SJSU_Facility_Development_Plan_2017.pdf

## Verification

Run `python tests/smoke_test.py` with Python and a JDK on PATH. The test compiles and starts the server in a temporary copy, checks HTTP responses and booking behavior, then stops and restarts it to verify persistence. It does not alter your bookings. GIS validation additionally requires shapely.

The shared ZIP excludes `bin/`, `data/`, Python caches, research PDFs, scratch scripts and the third-party research map client. Bookings start empty and are created locally when you use the demo. Source snapshots needed by the rebuild scripts are included.

Detailed research for all 57 map footprints: [FLOOR_RESEARCH.md](FLOOR_RESEARCH.md). Machine-readable audit: `sources/floor-research.json`. Historical counts are labeled; conflicting counts do not create floor buttons.

Detailed research for all 57 map footprints: [FLOOR_RESEARCH.md](FLOOR_RESEARCH.md). Machine-readable audit: `sources/floor-research.json`. Historical counts are labeled; conflicting counts do not create floor buttons.

Accuracy correction (September 23, 2026): published counts alone no longer generate floor buttons or equal-height 3D bands. Only Engineering has enabled level labels (1–4), documented in official Fall 2024 floor maps. Other buildings retain published evidence while level selection awaits verification. There are 21 assessment-based records and 9 counts with separate documentation. Source dates do not certify current conditions. This supersedes earlier descriptions of illustrative floor bands.

Accuracy correction (September 23, 2026): published counts alone no longer generate floor buttons or equal-height 3D bands. Only Engineering has enabled level labels (1–4), documented in official Fall 2024 floor maps. Other buildings retain published evidence while level selection awaits verification. There are 21 assessment-based records and 9 counts with separate documentation. Source dates do not certify current conditions. This supersedes earlier descriptions of illustrative floor bands.
