// Read-only Duncan Hall integration. All demo inventory is clearly labeled.
window.DuncanClassrooms = (() => {
  const el = id => document.getElementById(id);
  let rooms = [], version = 0;
  const make = (tag, text, className) => {
    const node = document.createElement(tag);
    node.textContent = text;
    if (className) node.className = className;
    return node;
  };
  async function api(url) {
    const response = await fetch(url, {signal: AbortSignal.timeout(15000)});
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Classroom data is unavailable.');
    return data;
  }
  async function load() {
    try { rooms = (await api('api/classrooms')).rooms; }
    catch { rooms = []; }
  }
  function hide() {
    version++;
    el('duncan-classroom').hidden = true;
  }
  function summary(buildingId, number) {
    if (buildingId !== 20) return '';
    const room = rooms.find(value => value.number === number);
    if (!room) return '';
    return (room.demo ? 'Demo · ' : '') + room.features.length + (room.features.length === 1 ? ' listed feature' : ' listed features');
  }
  async function show(number) {
    const request = ++version;
    el('duncan-classroom').hidden = false;
    el('duncan-title').textContent = 'Duncan Hall · Room ' + number;
    el('duncan-title').focus({preventScroll:true});
    el('duncan-classroom').scrollIntoView({behavior:matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth',block:'start'});
    el('duncan-status').textContent = 'Loading room details…';
    el('duncan-provenance').textContent = '';
    el('duncan-features').replaceChildren();
    el('duncan-reviews').replaceChildren();
    el('duncan-search-link').href = 'classrooms';
    try {
      if (!rooms.length) await load();
      if (request !== version) return;
      const found = rooms.filter(room => room.number === number);
      if (found.length !== 1) throw new Error(found.length ? 'Multiple records use this room number. Check classroom search.' : 'This room is not available in the database yet.');
      const data = await api('api/classrooms?id=' + found[0].id);
      if (request !== version) return;
      el('duncan-status').textContent = data.room.capacity == null
        ? 'Capacity is not verified.' : 'Capacity: ' + data.room.capacity;
      el('duncan-provenance').textContent = data.room.demo
        ? 'DEMO DATA · Features and quantities are simulated. The room number comes from a historical plan; current classroom use is not verified.'
        : 'Inventory from the project database. No listed feature does not necessarily mean it is unavailable.';
      el('duncan-search-link').href = 'classrooms?id=' + data.room.id;
      for (const feature of data.room.features) {
        const card = make('div', '', 'feature-item');
        card.append(make('strong', feature.name), make('span', 'Quantity: ' + feature.quantity));
        el('duncan-features').append(card);
      }
      if (!data.room.features.length) el('duncan-features').append(
        make('p', data.room.demo ? 'This demo room has no assigned features.' : 'No features recorded.', 'empty-state'));
      if (!data.reviews.length) el('duncan-reviews').append(make('p','No classroom reviews yet.','empty-state'));
      const labels = {wifi_rating:'Wi-Fi',cleanliness_rating:'Cleanliness',ac_rating:'AC',
        desk_space_rating:'Desk space',seating_comfort_rating:'Comfort',spaciousness_rating:'Space',
        lighting_rating:'Lighting',technology_rating:'Technology'};
      for (const review of data.reviews) {
        const card = make('article','','review-card');
        card.append(make('strong',review.author), make('p',review.text));
        const time = make('time',new Date(review.created).toLocaleDateString('en-US'));
        time.dateTime = review.created; card.append(time);
        const ratings = make('div','','rating-list');
        for (const [key,label] of Object.entries(labels))
          ratings.append(make('span',label + ': ' + (review.ratings[key] == null ? 'Not rated' : review.ratings[key] + '/5')));
        card.append(ratings); el('duncan-reviews').append(card);
      }
    } catch (error) {
      if (request !== version) return;
      el('duncan-status').textContent = error.message;
      const retry = make('button','Try again');
      retry.type = 'button'; retry.onclick = () => show(number);
      el('duncan-features').append(retry);
    }
  }
  return {load,show,hide,summary};
})();
