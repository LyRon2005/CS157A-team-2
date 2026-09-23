# Homepage verification — September 22, 2026

- Java 17 compilation and the existing isolated server smoke suite passed, including local assets, MIME types, input validation, concurrent conflicts and restart persistence.
- Browser: homepage loaded without console errors; confirm zoomed to King Library; cancel preserved the previous selection; floor 3 highlighted; direct map click opened the correct confirmation.
- Engineering displayed unavailable floor information rather than an inferred count. Empty search showed a no-results message. Overview restored the entrance view.
- Responsive browser check at 390 × 844: no horizontal overflow; selected-building panel moves below the visible model.
- Seven official-source floor records were added separately from historical OSM tags. Equal-height bands remain schematic, and unknown exterior heights remain flat footprints.

The homepage does not call the booking API. The earlier Discord ZIP has not been rebuilt for this revision.

## Previous package verification

### Release checks

Checks performed for the Discord sharing package:

- Java compilation targeting Java 17.
- Static assets, JSON and GeoJSON content types, and local MapLibre delivery.
- English interface and server messages.
- Empty booking store in a fresh copy.
- Valid bookings, overlapping bookings, adjacent slots and weekday class conflicts.
- Invalid room IDs, dates, times, blank names and control characters.
- Two concurrent requests for the same room and slot: one succeeds, one conflicts.
- JSON escaping and persisted bookings after an actual server restart.
- Static path containment and unsupported HTTP methods.
- GIS polygon validity, unique IDs, dimensions, registry coverage and height-source rules.
- Main Campus-only view, fixed camera and building selection.
- Source/coverage page and empty-search behavior.

The package does not include test bookings or compiled classes. These checks do not certify complete campus coverage, current surveyed heights, production security, or official room reservation integration. See README.md for known limits.

## Floor research update - September 22, 2026

Audited 57 footprints: 30 numerical counts (22 historical), 10 additional evidence records, 17 unresolved. Validated IDs, positive integer counts and source URLs. JavaScript syntax checks passed. Browser verified Engineering confirmation and Floor 4 selection; Science Building shows unresolved status without floor buttons. Current floor layouts and elevations remain unverified.
