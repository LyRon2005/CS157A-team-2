// Source-backed plans. Duncan Hall also reads room features and reviews from MySQL.
(() => {
  const el=id=>document.getElementById(id);
  let building=null, directory=null, floor=null, roomRows=[], roomLoading=false, roomError=false, roomVersion=0;
  const make=(tag,content,cls)=>{const node=document.createElement(tag);if(content)node.textContent=content;if(cls)node.className=cls;return node;};
  function roomSelected(room){
    window.DuncanClassrooms.hide();
    window.ClassroomOverlay.close(el('classroom-preview'));
    if(directory?.databaseId===20) window.DuncanClassrooms.show(room);
    el('classroom-title').textContent=`${building.name} · Room ${room.number}`;
    if(directory?.databaseId!==20) window.ClassroomOverlay.open(el('classroom-preview'));
    for(const pin of el('plan-frame').querySelectorAll('button'))pin.setAttribute('aria-pressed',String(pin.dataset.room===room.number));
    for(const button of el('room-list').querySelectorAll('button'))button.setAttribute('aria-pressed',String(button.dataset.room===room.number));
    el('plan-status').textContent=room.pin?`Room ${room.number} selected.`:`Room ${room.number} selected.`;
  }
  function rooms(){
    el('room-list').replaceChildren();
    const query=el('room-search').value.trim().toLowerCase();
    if(roomLoading){el('room-list').append(make('p','Loading rooms…','empty-state'));return;}
    if(roomError){const retry=make('button','Retry loading rooms');retry.onclick=()=>selectFloor(floor);el('room-list').append(retry);return;}
    const available=roomRows.filter(r=>r.number.toLowerCase().includes(query));
    if(!available.length){el('room-list').append(make('p',query?'No matching rooms.':'No rooms available.','empty-state'));return;}
    for(const room of available){const button=make('button',null,'room-card');button.type='button';button.dataset.room=room.number;button.setAttribute('aria-pressed','false');button.append(make('strong',`Room ${room.number}`),make('small','View room →'));const summary=room.features?.length ? room.features.length+' amenities' : '';if(summary)button.append(make('small',summary,'feature-summary'));button.onclick=()=>roomSelected(room);el('room-list').append(button);}
  }
  async function selectFloor(value){
    const request=++roomVersion;roomRows=[];roomLoading=true;roomError=false;
    window.DuncanClassrooms.hide();
    floor=value;el('floor-title').textContent=value.label;
    el('plan-large').hidden=!value.image;if(value.image)el('plan-large').href=value.image;
    el('plan-source').href=value.source;el('plan-source').hidden=!value.source;
    el('room-search').value='';window.ClassroomOverlay.close(el('classroom-preview'));el('plan-status').textContent='';
    for(const button of el('floor-list').children)button.setAttribute('aria-pressed',String(button.dataset.level===String(value.level)));
    el('plan-frame').replaceChildren();
    if(value.image){
      const image=make('img');image.src=value.image;image.alt=`${building.name}, ${value.label}: published floor plan${value.historical?' (historical)':''}`;
      image.onerror=()=>{el('plan-status').textContent='Unable to load the floor plan. Try opening the full plan.';};
      el('plan-frame').append(image);
    } else {el('plan-frame').append(make('div','Floor plan coming soon.','plan-placeholder'));}
    rooms();
    try {
      if (!directory.databaseId) { roomRows=[]; }
      else {
        const response=await fetch('api/classrooms?'+new URLSearchParams({buildingId:directory.databaseId,floor:value.level}),{signal:AbortSignal.timeout(15000)});
        if(!response.ok)throw Error('Rooms unavailable');
        const data=await response.json();if(request!==roomVersion)return;
        roomRows=data.rooms;
      }
    }catch(error){if(request!==roomVersion)return;roomError=true;}
    if(request!==roomVersion)return;roomLoading=false;rooms();
  }
  function indoor(){
    if(!building || directory?.teaching !== true || directory.roomsAvailable === false)return;
    document.body.classList.add('indoor-open');el('indoor-view').hidden=false;el('indoor-title').textContent=building.name;
    el('indoor-title').focus({preventScroll:true});el('indoor-view').scrollIntoView({behavior:'instant',block:'start'});
    const figure=el('lobby-photo');figure.replaceChildren();
    const interior=directory.interior,exterior=window.BUILDING_PHOTOS[building.id];
    const photo=interior||exterior;
    if(photo){const frame=make('div',null,'interior-image');const image=make('img');image.src=photo.src;image.alt=interior?interior.caption:`${building.name} exterior`;
      if(interior?.rotate)frame.classList.add('rotated-photo');image.onerror=()=>{frame.replaceChildren(make('p','Photo unavailable.'));};frame.append(image);
      const caption=make('figcaption',interior?'Interior':'Exterior');
      const link=make('a','Photo source ↗');link.href=photo.source;link.target='_blank';link.rel='noreferrer';caption.append(link);figure.append(frame,caption);
    }else figure.append(make('div','Photo coming soon.','lobby-placeholder'));
    el('floor-list').replaceChildren();floor=null;window.ClassroomOverlay.close(el('classroom-preview'));
    for(const value of directory.floors||[]){const button=make('button',value.label);button.type='button';button.dataset.level=value.level;button.onclick=()=>selectFloor(value);el('floor-list').append(button);}
    if(directory.floors?.length)selectFloor(directory.floors[0]);
    else{el('floor-list').append(make('p','Floor directory coming soon.'));el('floor-title').textContent='Inside this building';el('plan-source').hidden=true;el('plan-large').hidden=true;el('plan-frame').replaceChildren();el('plan-status').textContent='';rooms();}
  }
  window.addEventListener('building-cleared',()=>{roomVersion++;window.DuncanClassrooms.hide();window.ClassroomOverlay.close(el('classroom-preview'));});
  el('room-search').oninput=rooms;
  el('change-building').onclick=()=>window.CampusNavigation.overview();
  el('browse-rooms').onclick=indoor;
  el('back-building').onclick=()=>{roomVersion++;window.DuncanClassrooms.hide();window.ClassroomOverlay.close(el('classroom-preview'));document.body.classList.remove('indoor-open');el('indoor-view').hidden=true;window.CampusNavigation.refocus();el('browse-rooms').focus({preventScroll:true});el('map').scrollIntoView({behavior:'instant',block:'start'});};
  window.addEventListener('building-selected',e=>{
    window.DuncanClassrooms.hide();
    roomVersion++;roomRows=[];roomLoading=false;roomError=false;building=e.detail;directory=window.BUILDING_DIRECTORY[building.id]||{floors:[]};
    const paused = directory.roomsAvailable === false;
    el('browse-rooms').hidden=directory.teaching !== true || paused;
    el('exploration-note').hidden=directory.teaching === true && !paused;
    el('exploration-note').textContent=paused ? 'Room information is temporarily unavailable.' : 'Room browsing is available for teaching buildings.';
    el('building-data-note').textContent='';
  });
})();
