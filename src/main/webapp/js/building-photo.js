// Every map feature gets a visual. Never substitute a different building's photo.
window.showBuildingPhoto = function (building) {
  const figure = document.getElementById('building-photo');
  const status = document.getElementById('photo-status');
  const photo = window.BUILDING_PHOTOS[building.id];
  figure.replaceChildren(); figure.hidden = true; status.hidden = false;
  status.textContent = 'Loading building image…';
  function outline(message) {
    figure.replaceChildren();
    const ns = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 640 320');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', `${building.name}: mapped footprint, not a photograph`);
    svg.classList.add('building-outline');
    const polygons = building.geometry.type === 'Polygon' ? [building.geometry.coordinates] : building.geometry.coordinates;
    const points = polygons.flat(2), cosine = Math.cos(37.335 * Math.PI / 180);
    const xs = points.map(p => p[0] * cosine), ys = points.map(p => -p[1]);
    const minX = Math.min(...xs), minY = Math.min(...ys);
    const width = Math.max(...xs) - minX, height = Math.max(...ys) - minY;
    const scale = Math.min(460 / (width || 1), 210 / (height || 1));
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', polygons.flatMap(poly => poly.map(ring =>
      ring.map((p, i) => `${i ? 'L' : 'M'}${(p[0] * cosine - minX) * scale + (640 - width * scale) / 2},${(-p[1] - minY) * scale + (280 - height * scale) / 2}`).join(' ') + ' Z'
    )).join(' '));
    path.setAttribute('fill-rule', 'evenodd'); svg.append(path);
    const label = document.createElementNS(ns, 'text');
    label.setAttribute('x', '320');label.setAttribute('y', '302');label.setAttribute('text-anchor', 'middle');
    label.textContent = 'MAPPED FOOTPRINT · NOT A PHOTOGRAPH';svg.append(label);
    const caption = document.createElement('figcaption');
    caption.textContent = message + ' Outline: © OpenStreetMap contributors.';
    figure.append(svg, caption);figure.hidden = false;status.hidden = true;
  }
  if (!photo) { outline('A verified building photo is not available yet.'); return; }
  const image = document.createElement('img'), kind = photo.kind || 'exterior';
  image.alt = `${building.name} — ${kind === 'map' ? 'official location map' : kind + ' photograph'}`;
  image.classList.toggle('location-image', kind === 'map');
  image.width = 1000;image.height = 750;image.decoding = 'async';
  const caption = document.createElement('figcaption'), source = document.createElement('a');
  source.href = photo.source;source.target = '_blank';source.rel = 'noreferrer';
  source.textContent = `${kind === 'map' ? 'Map' : 'Photo'}: ${photo.author} ↗`;
  const license = document.createElement('a');
  license.href = photo.licenseUrl || photo.source;license.target = '_blank';license.rel = 'noreferrer';license.textContent = photo.license;
  const detail = kind === 'map' ? ' · Official location diagram, not a building photograph.'
    : kind === 'interior' ? ' · Interior reference; not an exterior view.'
    : ' · Resized for display; exterior may differ today.';
  caption.append(source, license, document.createTextNode(detail));figure.append(image, caption);
  image.onload = () => { if (image.parentElement !== figure) return; figure.hidden = false;status.hidden = true; };
  image.onerror = () => { if (image.parentElement === figure) outline('The photograph could not load.'); };
  image.src = photo.src;
};
