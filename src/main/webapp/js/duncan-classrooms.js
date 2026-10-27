// Native dialogs keep the selected floor behind the room overlay.
window.ClassroomOverlay = (() => {
  const dialogs=[document.getElementById('duncan-classroom'),document.getElementById('classroom-preview')];
  const origins=new WeakMap();
  for(const dialog of dialogs) dialog.addEventListener('close',()=>{
    if(!dialogs.some(value=>value.open))document.body.classList.remove('room-overlay-open');
    const origin=origins.get(dialog);
    if(origin?.isConnected && origin.getClientRects().length)origin.focus({preventScroll:true});
  });
  function open(dialog) {
    if(!dialog.open){origins.set(dialog,document.activeElement);dialog.showModal();}
    document.body.classList.add('room-overlay-open');
    const body=dialog.querySelector('.room-overlay-body');if(body)body.scrollTop=0;
    dialog.querySelector('h2').focus({preventScroll:true});
  }
  document.getElementById('close-classroom').onclick=()=>dialogs[0].close();
  document.getElementById('close-classroom-preview').onclick=()=>dialogs[1].close();
  return {open,close:dialog=>{if(dialog.open)dialog.close();}};
})();

// Duncan Hall room details and review selection. All demo inventory is clearly labeled.
window.DuncanClassrooms = (() => {
  const el = id => document.getElementById(id);
  let version = 0;
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
  const available = () => window.BUILDING_DIRECTORY?.W25774763?.roomsAvailable !== false;
  function hide() {
    version++;
    window.ClassroomReviews?.select(null);
    window.ClassroomOverlay.close(el('duncan-classroom'));
  }
  async function show(room) {
    if (!available()) { hide(); return; }
    const request = ++version;
    window.ClassroomReviews?.select(null);
    el('duncan-title').textContent = 'Duncan Hall · Room ' + room.number;
    window.ClassroomOverlay.open(el('duncan-classroom'));
    el('duncan-classroom').querySelector('.room-overview').hidden = false;
    el('duncan-classroom').classList.remove('reviews-only');
    el('duncan-status').textContent = 'Loading room details…';
    el('duncan-features').replaceChildren();
    el('duncan-reviews').replaceChildren();
    el('duncan-search-link').href = 'classrooms';
    try {
      const data = await api('api/classrooms?id=' + room.id);
      if (request !== version) return;
      el('duncan-status').textContent = data.room.capacity == null
        ? '' : 'Capacity: ' + data.room.capacity;
      el('duncan-search-link').href = 'classrooms?id=' + data.room.id;
      for (const feature of data.room.features) {
        const card = make('div', '', 'feature-item');
        card.append(make('strong', feature.name), make('span', 'Quantity: ' + feature.quantity));
        el('duncan-features').append(card);
      }
      if (!data.room.features.length) el('duncan-features').append(
        make('p', 'Amenities coming soon.', 'empty-state'));
      const noDetails = data.room.capacity == null && !data.room.features.length;
      el('duncan-classroom').querySelector('.room-overview').hidden = noDetails;
      el('duncan-classroom').classList.toggle('reviews-only', noDetails);
      window.ClassroomReviews.select({databaseId:data.room.id,name:'Duncan Hall · Room '+data.room.number});
    } catch (error) {
      if (request !== version) return;
      el('duncan-status').textContent = error.message;
      const retry = make('button','Try again');
      retry.type = 'button'; retry.onclick = () => show(room);
      el('duncan-features').append(retry);
    }
  }
  el('duncan-classroom').addEventListener('close',()=>{version++;window.ClassroomReviews?.select(null);});
  return {show,hide};
})();
