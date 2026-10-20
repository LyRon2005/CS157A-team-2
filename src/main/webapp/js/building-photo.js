/* This panel only reads the local photo catalog. It never requests user or review data.
 * Missing photos have an honest fallback. We do not reuse another building's image.
 */
window.showBuildingPhoto = function (building) {
  const figure = document.getElementById('building-photo');
  const status = document.getElementById('photo-status');
  const photo = window.BUILDING_PHOTOS[building.id];
  figure.replaceChildren();
  figure.hidden = true;
  status.hidden = false;
  status.textContent = photo ? 'Loading building photo…' : 'A verified teaching-building photo is not available here yet.';
  // Reviews remain empty, including when the user switches buildings.
  document.getElementById('building-reviews').replaceChildren();
  if (!photo) return;
  const image = document.createElement('img');
  image.alt = `${building.name} — exterior photograph`;
  image.width = 1000;
  image.height = 750;
  image.decoding = 'async';
  const caption = document.createElement('figcaption');
  const source = document.createElement('a');
  source.href = photo.source;
  source.target = '_blank';
  source.rel = 'noreferrer';
  source.textContent = `Photo: ${photo.author} ↗`;
  const license = document.createElement('a');
  license.href = photo.licenseUrl || photo.source;
  license.target = '_blank';
  license.rel = 'noreferrer';
  license.textContent = photo.license;
  caption.append(source,license,document.createTextNode(' · Resized for display; exterior may differ today.'));
  figure.append(image,caption);
  image.onload = () => {
    if (image.parentElement !== figure) return;
    figure.hidden = false;
    status.hidden = true;
  };
  image.onerror = () => {
    if (image.parentElement !== figure) return;
    figure.hidden = true;
    status.textContent = 'The photo could not load. Try selecting this building again from the overview.';
  };
  image.src = photo.src;
};
