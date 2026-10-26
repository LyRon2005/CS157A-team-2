// Source-backed room browser. Classroom reviews are a UI preview only.
(() => {
  const el=id=>document.getElementById(id);
  let building=null, directory=null, floor=null;
  const make=(tag,content,cls)=>{const node=document.createElement(tag);if(content)node.textContent=content;if(cls)node.className=cls;return node;};
  function roomSelected(room){
    el('classroom-preview').hidden=false;el('classroom-title').textContent=`${building.name} · Room ${room.number}`;
    el('classroom-title').focus({preventScroll:true});
    for(const pin of el('plan-frame').querySelectorAll('button'))pin.setAttribute('aria-pressed',String(pin.dataset.room===room.number));
    for(const button of el('room-list').querySelectorAll('button'))button.setAttribute('aria-pressed',String(button.dataset.room===room.number));
    el('plan-status').textContent=room.pin?`Room ${room.number} is highlighted on the published plan.`:`Room ${room.number} selected. A verified pin location is not available; use the labels on the source map.`;
  }
  function rooms(){
    el('room-list').replaceChildren();
    const query=el('room-search').value.trim().toLowerCase();
    const available=(floor?.rooms||[]).filter(r=>r.number.toLowerCase().includes(query));
    if(!available.length){el('room-list').append(make('p',query?'No matching rooms.':'A verified room list is not available for this floor.','empty-state'));return;}
    for(const room of available){const button=make('button',null,'room-card');button.type='button';button.dataset.room=room.number;button.setAttribute('aria-pressed','false');button.append(make('strong',`Room ${room.number}`),make('small',room.kind));button.onclick=()=>roomSelected(room);el('room-list').append(button);}
  }
  function selectFloor(value){
    floor=value;el('floor-title').textContent=value.label;el('plan-note').textContent=value.note;
    el('plan-large').hidden=!value.image;if(value.image)el('plan-large').href=value.image;
    el('plan-source').href=value.source;el('plan-source').hidden=!value.source;
    el('room-search').value='';el('classroom-preview').hidden=true;el('plan-status').textContent='';
    for(const button of el('floor-list').children)button.setAttribute('aria-pressed',String(button.dataset.level===String(value.level)));
    el('plan-frame').replaceChildren();
    if(value.image){
      const image=make('img');image.src=value.image;image.alt=`${building.name}, ${value.label}: published floor plan${value.historical?' (historical)':''}`;
      image.onerror=()=>{el('plan-status').textContent='The plan could not load. Open the university source above.';};
      el('plan-frame').append(image);
      for(const room of value.rooms||[])if(room.pin){const pin=make('button',room.number,'room-pin');pin.type='button';pin.dataset.room=room.number;pin.style.left=room.pin[0]+'%';pin.style.top=room.pin[1]+'%';pin.setAttribute('aria-label',`Select room ${room.number}`);pin.setAttribute('aria-pressed','false');pin.onclick=()=>roomSelected(room);el('plan-frame').append(pin);}
    } else {el('plan-frame').append(make('div','A verified floor plan is not available here yet.','plan-placeholder'));}
    rooms();
  }
  function indoor(){
    if(!building || directory?.teaching !== true)return;
    document.body.classList.add('indoor-open');el('indoor-view').hidden=false;el('indoor-title').textContent=building.name;
    el('indoor-title').focus({preventScroll:true});el('indoor-view').scrollIntoView({behavior:'instant',block:'start'});
    const figure=el('lobby-photo');figure.replaceChildren();
    const interior=directory.interior,exterior=window.BUILDING_PHOTOS[building.id];
    const photo=interior||exterior;
    if(photo){const frame=make('div',null,'interior-image');const image=make('img');image.src=photo.src;image.alt=interior?interior.caption:`${building.name} exterior; lobby photo unavailable`;
      if(interior?.rotate)frame.classList.add('rotated-photo');image.onerror=()=>{frame.replaceChildren(make('p','Photo unavailable.'));};frame.append(image);
      const caption=make('figcaption',interior?interior.caption:'Exterior reference · a verified main-lobby photo is not available yet.');
      const link=make('a','Photo source ↗');link.href=photo.source;link.target='_blank';link.rel='noreferrer';caption.append(link);figure.append(frame,caption);
    }else figure.append(make('div','Main-lobby photo not yet verified.','lobby-placeholder'));
    el('floor-list').replaceChildren();floor=null;el('classroom-preview').hidden=true;
    for(const value of directory.floors||[]){const button=make('button',value.label);button.type='button';button.dataset.level=value.level;button.onclick=()=>selectFloor(value);el('floor-list').append(button);}
    if(directory.floors?.length)selectFloor(directory.floors[0]);
    else{el('floor-list').append(make('p','Floor directory coming soon.'));el('floor-title').textContent='Inside this building';el('plan-note').textContent='No public floor directory has been verified for this building.';el('plan-source').hidden=true;el('plan-large').hidden=true;el('plan-frame').replaceChildren();el('plan-status').textContent='';rooms();}
  }
  el('room-search').oninput=rooms;
  el('change-building').onclick=()=>window.CampusNavigation.overview();
  el('browse-rooms').onclick=indoor;
  el('back-building').onclick=()=>{document.body.classList.remove('indoor-open');el('indoor-view').hidden=true;window.CampusNavigation.refocus();el('browse-rooms').focus({preventScroll:true});el('map').scrollIntoView({behavior:'instant',block:'start'});};
  window.addEventListener('building-selected',e=>{
    building=e.detail;directory=window.BUILDING_DIRECTORY[building.id]||{floors:[]};
    el('browse-rooms').hidden=directory.teaching !== true;
    el('exploration-note').hidden=directory.teaching === true;
    el('building-data-note').textContent=building.id==='W1078071070'?'8 stories confirmed by SJSU · 3D height uses the published planning envelope, not an as-built survey.':'';
  });
})();
