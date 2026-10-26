// Campus map and building selection. Indoor navigation lives in building-browser.js.
const $ = (id) => document.getElementById(id);
const scene = window.MAIN_SCENE, geo = scene.geo;
const buildings = geo.features.map((f) => ({
  ...f.properties,
  geometry: f.geometry,
}));
let map,
  ready = false,
  current = null,
  pending = null,
  selectedMarker = null;
const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const duration = reduced ? 0 : 1100;
// Remember the clicked building and ask for confirmation before moving the camera.
function requestBuilding(id) {
  const b = buildings.find((b) => b.id === id);
  if (!b || current?.id === id || $("confirm-dialog").open) return;
  pending = b;
  $("confirm-name").textContent = b.named ? b.name : "Campus building";
  $("confirm-dialog").returnValue = "";
  $("confirm-dialog").showModal();
}
// Only the Confirm action selects the building. Closing the dialog cancels the action.
$("confirm-dialog").addEventListener("close", () => {
  const b = pending;
  pending = null;
  if ($("confirm-dialog").returnValue === "confirm" && b) selectBuilding(b);
});
$("how").onclick = () => $("help-dialog").showModal();
for (const btn of document.querySelectorAll("[data-building]"))
  btn.onclick = () => requestBuilding(btn.dataset.building);
// Search the local building names and aliases. textContent keeps names as text, not HTML.
$("search").oninput = () => {
  const q = $("search").value.trim().toLowerCase();
  $("results").replaceChildren();
  $("no-results").hidden = true;
  if (!q) return;
  const matches = buildings.filter(
    (b) =>
      b.named &&
      [b.name, ...(b.aliases || [])].join(" ").toLowerCase().includes(q),
  );
  $("no-results").hidden = matches.length > 0;
  for (const b of matches) {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.textContent = b.name;
    btn.disabled = current?.id === b.id;
    btn.onclick = () => requestBuilding(b.id);
    $("results").append(btn);
  }
};
// Walk Polygon or MultiPolygon coordinates to find the rectangle around a building.
function bounds(b) {
  const pts = [];
  function visit(x) {
    if (typeof x[0] === "number") pts.push(x);
    else x.forEach(visit);
  }
  visit(b.geometry.coordinates);
  return [
    [Math.min(...pts.map((p) => p[0])), Math.min(...pts.map((p) => p[1]))],
    [Math.max(...pts.map((p) => p[0])), Math.max(...pts.map((p) => p[1]))],
  ];
}
// Color the selected building. Indoor plans are shown in a separate view.
function paintSelection() {
  if (!ready) return;
  map.setPaintProperty("campus-buildings", "fill-extrusion-color", [
    "case",
    ["==", ["get", "id"], current?.id || ""],
    "#c59b51",
    ["==", ["get", "category"], "housing"],
    window.CampusTime.buildingColor("#a7b8a8", "#527180"),
    ["==", ["get", "category"], "parking"],
    window.CampusTime.buildingColor("#91a4a1", "#435c73"),
    window.CampusTime.buildingColor("#6f9384", "#476c86"),
  ]);
}
// Zoom toward the selected building and leave room for the detail panel.
function focusBuilding() {
  if (!ready || !current || document.body.classList.contains("indoor-open")) return;
  const w = map.getContainer().clientWidth;
  const pad =
    window.innerWidth > 1000
      ? { left: 45, right: 480, top: 150, bottom: 100 }
      : { left: 28, right: 28, top: 100, bottom: 720 };
  const cam = map.cameraForBounds(bounds(current), {
    bearing: scene.bearing,
    padding: pad,
    maxZoom: w > 700 ? (current.renderHeight > 35 ? 18 : 18.5) : 17.8,
  });
  if (cam) map.flyTo({ ...cam, pitch: 52, bearing: scene.bearing, duration });
}
// Fill the detail panel and move the camera after confirmation.
function selectBuilding(b) {
    current = b;
    window.selectedBuilding = b;
    window.dispatchEvent(
      new CustomEvent("building-selected", {
        detail: b
      })
  );
  window.showBuildingPhoto(b);
  for (const btn of document.querySelectorAll("[data-building]"))
    btn.disabled = btn.dataset.building === b.id;
  $("search").value = "";
  $("results").replaceChildren();
  $("no-results").hidden = true;
  $("detail").hidden = false;
  $("map-caption").hidden = true;
  $("detail-name").textContent = b.named ? b.name : "Campus building";
  document.body.classList.add("building-open");
  $("reviews-heading").textContent = "Campus voices";
  map?.resize();
  paintSelection();
  if (ready) {
    selectedMarker?.remove();
    const el = document.createElement("div");
    el.className = "building-label";
    el.textContent = b.named ? b.name : "Campus building";
    selectedMarker = new maplibregl.Marker({
      element: el,
      anchor: "bottom",
      offset: [0, -12],
    })
      .setLngLat(b.center)
      .addTo(map);
    focusBuilding();
  }
  if (window.innerWidth <= 700)
    $("map").scrollIntoView({
      behavior: reduced ? "instant" : "smooth",
      block: "start",
    });
}
// Clear the selection and return to the fixed main-campus entrance view.
function overview() {
  current = null;
  window.selectedBuilding = null;
  document.body.classList.remove("building-open", "indoor-open");
  $("indoor-view").hidden = true;
  window.dispatchEvent(new CustomEvent("building-cleared"));
  for (const btn of document.querySelectorAll("[data-building]")) btn.disabled = false;
  if ($("search").value) $("search").oninput();
  $("detail").hidden = true;
  $("map-caption").hidden = false;
  selectedMarker?.remove();
  selectedMarker = null;
  if (!ready) return;
  paintSelection();
  const small = map.getContainer().clientWidth < 700;
  const cam = map.cameraForBounds(scene.bounds, {
    bearing: scene.bearing,
    padding: { top: 210, bottom: 125, left: 30, right: 30 },
  });
  if (cam)
    map.flyTo({
      ...cam,
      zoom: cam.zoom + (small ? 0.15 : 1.1),
      pitch: 42,
      bearing: scene.bearing,
      duration,
    });
}
$("reset").onclick = overview;
$("close-detail").onclick = overview;
window.CampusNavigation = { overview, refocus: () => { map?.resize(); focusBuilding(); } };
// Create the map once. Show a helpful fallback if the browser cannot render it.
try {
  map = new maplibregl.Map({
    container: "map",
    center: scene.center,
    zoom: 16,
    pitch: 42,
    bearing: scene.bearing,
    interactive: false,
    attributionControl: false,
    style: {
      version: 8,
      light: {
        anchor: "viewport",
        color: "#fff9e7",
        intensity: 0.45,
        position: [1.5, 210, 35],
      },
      sources: {},
      layers: [
        {
          id: "background",
          type: "background",
          paint: { "background-color": "#e6ece4" },
        },
      ],
    },
  });
  map.addControl(
    new maplibregl.AttributionControl({
      customAttribution:
        '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · <a href="sources.html">Data & credits</a>',
    }),
  );
  // Wait for MapLibre to be ready before adding data sources and drawing layers.
  map.on("load", () => {
    map.addSource("ground", { type: "geojson", data: scene.ground });
    // Draw flat paths, roads, trees and green areas below the 3D buildings.
    for (const [kind, color] of [
      ["campus", "#f0eedf"],
      ["plaza", "#e6dfcc"],
      ["green", "#bfcea8"],
      ["road", "#abb9b5"],
      ["path", "#faf7e9"],
      ["water", "#8ebfc3"],
      ["tree", "#7d9f70"],
      ["tree-core", "#577c59"],
    ])
      map.addLayer({
        id: "ground-" + kind,
        type: "fill",
        source: "ground",
        filter: ["==", ["get", "kind"], kind],
        paint: { "fill-color": color },
      });
    // Add the building footprints. renderHeight is for drawing, not a room floor measurement.
    map.addSource("campus", { type: "geojson", data: geo });
    map.addLayer({
      id: "footprints",
      type: "fill",
      source: "campus",
      paint: { "fill-color": "#c1cebd" },
    });
    map.addLayer({
      id: "outlines",
      type: "line",
      source: "campus",
      paint: { "line-color": "#8b9d88", "line-width": 0.6 },
    });
    map.addLayer({
      id: "campus-buildings",
      type: "fill-extrusion",
      source: "campus",
      paint: {
        "fill-extrusion-height": ["get", "renderHeight"],
        "fill-extrusion-color": window.CampusTime.buildingColor("#6f9384", "#476c86"),
        "fill-extrusion-opacity": 1,
      },
    });
    // Find the rendered shape below the pointer, then open the confirmation dialog.
    map.on("click", (e) => {
      const hits = map.queryRenderedFeatures(e.point, {
        layers: ["campus-buildings", "footprints"],
      });
      if (hits.length) requestBuilding(hits[0].properties.id);
    });
    map.on("mousemove", (e) => {
      const hits = map.queryRenderedFeatures(e.point, {
        layers: ["campus-buildings", "footprints"],
      });
      map.getCanvas().style.cursor = hits.length && hits[0].properties.id !== current?.id ? "pointer" : "";
    });
    const el = document.createElement("div");
    el.className = "gate-label";
    el.textContent = "BOCCARDO GATE ↗";
    new maplibregl.Marker({ element: el, anchor: "top" })
      .setLngLat(scene.gate)
      .addTo(map);
    ready = true;
    window.CampusTime.attach(map, paintSelection);
    window.CampusLife.attach(map);
    $("map-loading").hidden = true;
    // Restore a selection made before the map finished loading.
    const saved = current;
    overview();
    if (saved) selectBuilding(saved);
    map.on("resize", () => {
      if (current) focusBuilding();
      else overview();
    });
  });
  // Stop the loading message and show the map error notice if rendering fails.
  map.on("error", () => {
    $("map-error").hidden = false;
    $("map-loading").hidden = true;
  });
} catch (error) {
  $("map-error").hidden = false;
  $("map-loading").hidden = true;
}
