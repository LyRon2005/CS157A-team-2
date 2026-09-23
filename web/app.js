const $=id=>document.getElementById(id);
const scene=window.MAIN_SCENE, geo=scene.geo, floorData=window.DEMO_FLOORS;
const buildings=geo.features.map(f=>({...f.properties,geometry:f.geometry}));
let map,ready=false,current=null,pending=null,selectedMarker=null;
const empty={type:'FeatureCollection',features:[]};
const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const duration=reduced?0:1100;
function requestBuilding(id){
 const b=buildings.find(b=>b.id===id);if(!b||$('confirm-dialog').open)return;
 pending=b;$('confirm-name').textContent=b.named?b.name:'Campus building';
 $('confirm-dialog').returnValue='';$('confirm-dialog').showModal();
}
$('confirm-dialog').addEventListener('close',()=>{const b=pending;pending=null;if($('confirm-dialog').returnValue==='confirm'&&b)selectBuilding(b);});
$('how').onclick=()=>$('help-dialog').showModal();
for(const btn of document.querySelectorAll('[data-building]'))btn.onclick=()=>requestBuilding(btn.dataset.building);
$('search').oninput=()=>{
 const q=$('search').value.trim().toLowerCase();$('results').replaceChildren();$('no-results').hidden=true;if(!q)return;
 const matches=buildings.filter(b=>b.named&&[b.name,...(b.aliases||[])].join(' ').toLowerCase().includes(q));
 $('no-results').hidden=matches.length>0;
 for(const b of matches){const btn=document.createElement('button');btn.type='button';btn.textContent=b.name;btn.onclick=()=>requestBuilding(b.id);$('results').append(btn);}
};
function bounds(b){const pts=[];function visit(x){if(typeof x[0]==='number')pts.push(x);else x.forEach(visit);}visit(b.geometry.coordinates);return [[Math.min(...pts.map(p=>p[0])),Math.min(...pts.map(p=>p[1]))],[Math.max(...pts.map(p=>p[0])),Math.max(...pts.map(p=>p[1]))]];}
function paintSelection(){if(!ready)return;const sliced=current&&floorFeatures(current).features.length>0;map.setPaintProperty('campus-buildings','fill-extrusion-height',['case',['==',['get','id'],sliced?current.id:''],0,['get','renderHeight']]);map.setPaintProperty('campus-buildings','fill-extrusion-color',['case',['==',['get','id'],current?.id||''],'#c59b51',['==',['get','category'],'housing'],'#a7b8a8',['==',['get','category'],'parking'],'#91a4a1','#6f9384']);}
function focusBuilding(){if(!ready||!current)return;const w=map.getContainer().clientWidth;const pad=window.innerWidth>1000?{left:75,right:345,top:130,bottom:150}:{left:28,right:28,top:85,bottom:335};const cam=map.cameraForBounds(bounds(current),{bearing:scene.bearing,padding:pad,maxZoom:w>700?18.8:18});if(cam)map.flyTo({...cam,pitch:52,bearing:scene.bearing,duration});}
function floorFeatures(b,selected=0){const f=floorData[b.id];if(!f||!b.height)return empty;const step=Number(b.renderHeight)/f.count;return {type:'FeatureCollection',features:f.levels.map((n,i)=>({type:'Feature',geometry:b.geometry,properties:{id:b.id,floor:n,base:i*step,top:(i+1)*step-Math.min(.18,step*.08),selected:n===selected}}))};}
function selectFloor(n){for(const btn of $('floors').children)btn.setAttribute('aria-pressed',String(Number(btn.dataset.floor)===n));$('floor-message').textContent=`Floor ${n} selected. Room details will be added later.`;if(ready&&current)map.getSource('floor-bands').setData(floorFeatures(current,n));}
function selectBuilding(b){
 current=b;$('search').value='';$('results').replaceChildren();$('no-results').hidden=true;$('detail').hidden=false;$('map-caption').hidden=true;
 $('detail-name').textContent=b.named?b.name:'Campus building';$('floors').replaceChildren();const f=floorData[b.id];
 $('floor-summary').textContent=f?(f.summary||`${f.count} above-ground floors${f.variableHeight?' at the highest point':''}`):'Floor information is being updated.';
 $('floor-message').textContent='Choose a floor · schematic demo preview.';
 $('floor-note').textContent=f.note+(!b.height?' Exterior height is unknown, so this building remains a footprint plate.':'');
 $('floor-source').hidden=!f;if(f)$('floor-source').href=f.source;
 for(const n of f?.levels||[]){const btn=document.createElement('button');btn.type='button';btn.textContent=String(n).padStart(2,'0');btn.dataset.floor=n;btn.setAttribute('aria-label',`Floor ${n}`);btn.setAttribute('aria-pressed','false');btn.onclick=()=>selectFloor(n);$('floors').append(btn);}
 paintSelection();if(ready){map.getSource('floor-bands').setData(floorFeatures(b));selectedMarker?.remove();const el=document.createElement('div');el.className='building-label';el.textContent=b.named?b.name:'Campus building';selectedMarker=new maplibregl.Marker({element:el,anchor:'bottom',offset:[0,-12]}).setLngLat(b.center).addTo(map);focusBuilding();}
 if(window.innerWidth<=700)$('map').scrollIntoView({behavior:reduced?'instant':'smooth',block:'start'});
}
function overview(){current=null;$('detail').hidden=true;$('map-caption').hidden=false;selectedMarker?.remove();selectedMarker=null;if(!ready)return;paintSelection();map.getSource('floor-bands').setData(empty);const small=map.getContainer().clientWidth<700;const cam=map.cameraForBounds(scene.bounds,{bearing:scene.bearing,padding:{top:85,bottom:125,left:30,right:30}});if(cam)map.flyTo({...cam,zoom:cam.zoom+(small?.15:.8),pitch:42,bearing:scene.bearing,duration});}
$('reset').onclick=overview;$('close-detail').onclick=overview;
try{
 map=new maplibregl.Map({container:'map',center:scene.center,zoom:16,pitch:42,bearing:scene.bearing,interactive:false,attributionControl:false,style:{version:8,light:{anchor:'viewport',color:'#fff9e7',intensity:.45,position:[1.5,210,35]},sources:{},layers:[{id:'background',type:'background',paint:{'background-color':'#e6ece4'}}]}});
 map.addControl(new maplibregl.AttributionControl({customAttribution:'© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · <a href="/sources.html">Data & credits</a>'}));
 map.on('load',()=>{
  map.addSource('ground',{type:'geojson',data:scene.ground});
  for(const [kind,color] of [['campus','#f0eedf'],['plaza','#e6dfcc'],['green','#bfcea8'],['road','#abb9b5'],['path','#faf7e9'],['water','#8ebfc3'],['tree','#7d9f70'],['tree-core','#577c59']])map.addLayer({id:'ground-'+kind,type:'fill',source:'ground',filter:['==',['get','kind'],kind],paint:{'fill-color':color}});
  map.addSource('campus',{type:'geojson',data:geo});map.addLayer({id:'footprints',type:'fill',source:'campus',paint:{'fill-color':'#c1cebd'}});map.addLayer({id:'outlines',type:'line',source:'campus',paint:{'line-color':'#8b9d88','line-width':.6}});
  map.addLayer({id:'campus-buildings',type:'fill-extrusion',source:'campus',paint:{'fill-extrusion-height':['get','renderHeight'],'fill-extrusion-color':'#6f9384','fill-extrusion-opacity':1}});
  map.addSource('floor-bands',{type:'geojson',data:empty});map.addLayer({id:'floor-bands',type:'fill-extrusion',source:'floor-bands',paint:{'fill-extrusion-base':['get','base'],'fill-extrusion-height':['get','top'],'fill-extrusion-color':['case',['get','selected'],'#ffe7a2','#d7b977'],'fill-extrusion-opacity':1}});
  map.on('click',e=>{const hits=map.queryRenderedFeatures(e.point,{layers:['floor-bands','campus-buildings','footprints']});if(hits.length)requestBuilding(hits[0].properties.id);});
  map.on('mousemove',e=>{map.getCanvas().style.cursor=map.queryRenderedFeatures(e.point,{layers:['floor-bands','campus-buildings','footprints']}).length?'pointer':'';});
  const el=document.createElement('div');el.className='gate-label';el.textContent='BOCCARDO GATE ↗';new maplibregl.Marker({element:el,anchor:'top'}).setLngLat(scene.gate).addTo(map);
  ready=true;$('map-loading').hidden=true;const saved=current;overview();if(saved)selectBuilding(saved);
  map.on('resize',()=>{if(current)focusBuilding();else overview();});
 });
 map.on('error',()=>{$('map-error').hidden=false;$('map-loading').hidden=true;});
}catch(error){$('map-error').hidden=false;$('map-loading').hidden=true;}
