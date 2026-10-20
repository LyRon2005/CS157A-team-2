/* Campus atmosphere is a frontend preview only.
 * The clock uses San Jose time, including daylight saving time.
 * Colors are artistic day/night lighting, not measured sunrise or weather data.
 * Call attach() only after the map layers exist. No server requests are made.
 */
window.CampusTime = (() => {
  let map, repaint, minutes = 720;
  const area = document.querySelector('.map-area');
  const slider = document.getElementById('campus-time');
  const live = document.getElementById('time-live');
  const output = document.getElementById('time-output');
  const motion = document.getElementById('campus-motion');
  const preference = matchMedia('(prefers-reduced-motion: reduce)');
  const clock = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Los_Angeles', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  });
  const colors = {
    day: ['#e6ece4','#f0eedf','#e6dfcc','#bfcea8','#abb9b5','#faf7e9','#8ebfc3','#7d9f70','#577c59'],
    dusk: ['#d4b8ad','#dfc8ae','#ccb798','#a2aa83','#929597','#f0d7b0','#819faa','#718c70','#526c59'],
    night: ['#101f32','#20384a','#2b4052','#25473f','#354857','#51616a','#284f66','#365c4c','#254738']
  };
  // Blend colors continuously so dragging the clock has no sudden theme jump.
  function mix(a, b, amount) {
    return '#' + [1,3,5].map(i => Math.round(parseInt(a.slice(i,i+2),16) * (1-amount)
      + parseInt(b.slice(i,i+2),16) * amount).toString(16).padStart(2,'0')).join('');
  }
  function phase() {
    const h = minutes / 60;
    if (h < 5 || h >= 21) return ['night','night',0];
    if (h < 7) return ['night','dusk',(h-5)/2];
    if (h < 9) return ['dusk','day',(h-7)/2];
    if (h < 17) return ['day','day',0];
    if (h < 19) return ['day','dusk',(h-17)/2];
    return ['dusk','night',(h-19)/2];
  }
  function buildingColor(day, night) {
    const [a,b,t] = phase();
    const strength = {day:0,dusk:0.32,night:1};
    return mix(day,night,strength[a]*(1-t)+strength[b]*t);
  }
  function render() {
    const h = minutes / 60;
    const night = h < 6 || h >= 20;
    area.dataset.period = night ? 'night' : h < 9 || h >= 17 ? 'dusk' : 'day';
    const text = `${String(Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}`;
    output.textContent = text;
    slider.value = minutes;
    slider.setAttribute('aria-valuetext', `${text}, San Jose time`);
    document.getElementById('time-status').textContent = `${live.checked ? 'Live' : 'Preview'} · San José`;
    document.getElementById('time-symbol').textContent = night ? '☾' : '☀';
    if (!map) return;
    const [a,b,t] = phase();
    const layers = ['background','campus','plaza','green','road','path','water','tree','tree-core'];
    layers.forEach((name,i) => map.setPaintProperty(name==='background' ? name : 'ground-'+name,
      name==='background' ? 'background-color' : 'fill-color',mix(colors[a][i],colors[b][i],t)));
    map.setPaintProperty('footprints','fill-color',buildingColor('#c1cebd','#385568'));
    map.setPaintProperty('outlines','line-color',buildingColor('#8b9d88','#668293'));
    // Warm path lights are decorative markers, not surveyed lamp locations.
    const glow = h < 6 || h > 19 ? 0.85 : h < 8 || h > 17 ? 0.35 : 0;
    map.setPaintProperty('path-light-glow','circle-opacity',glow * 0.45);
    map.setPaintProperty('path-light-core','circle-opacity',glow);
    map.setLight({anchor:'map',color:night?'#b7d6ff':'#fff0d0',intensity:night?0.25:0.5,
      position:[1.5,(h/24)*360,night?60:35]});
    repaint();
  }
  function sync() {
    if (!live.checked) return;
    const parts = clock.formatToParts(new Date());
    minutes = Number(parts.find(p=>p.type==='hour').value)*60 + Number(parts.find(p=>p.type==='minute').value);
    render();
  }
  // Moving the slider switches to preview. Checking Live immediately restores local campus time.
  slider.addEventListener('input',()=>{live.checked=false;minutes=Number(slider.value);render();});
  live.addEventListener('change',()=>{if(live.checked)sync();else render();});
  function setMotion() {
    area.classList.toggle('motion-paused', !motion.checked || preference.matches || document.hidden);
  }
  motion.checked = !preference.matches;
  motion.addEventListener('change',setMotion);
  preference.addEventListener('change',setMotion);
  document.addEventListener('visibilitychange',()=>{setMotion();if(!document.hidden)sync();});
  // Use one light timer. Decorative animations are CSS and pause in a hidden tab.
  setInterval(()=>{if(!document.hidden)sync();},30000);
  setMotion(); sync();
  return { buildingColor, attach(instance, callback) {
    map=instance;repaint=callback;
    const paths = window.MAIN_SCENE.ground.features.filter(f=>f.properties.kind==='path');
    const lights = paths.filter((_,i)=>i%18===0).map(f=>{
      const ring=f.geometry.coordinates[0];
      // Use a path vertex so each light stays attached to the campus during camera movement.
      return {type:'Feature',properties:{},geometry:{type:'Point',coordinates:ring[0]}};
    });
    map.addSource('path-lights',{type:'geojson',data:{type:'FeatureCollection',features:lights}});
    map.addLayer({id:'path-light-glow',type:'circle',source:'path-lights',paint:{
      'circle-radius':['interpolate',['linear'],['zoom'],15,5,19,18],
      'circle-color':'#ffcf75','circle-blur':0.85,'circle-opacity':0}},'campus-buildings');
    map.addLayer({id:'path-light-core',type:'circle',source:'path-lights',paint:{
      'circle-radius':2,'circle-color':'#ffe7b0','circle-opacity':0}},'campus-buildings');
    render();
  } };
})();
