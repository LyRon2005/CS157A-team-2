/* Decorative campus life. These vehicles and Spartans are not live location data.
 * Routes come from the existing road/path polygons. No backend is used.
 * All figures are map geometry, so zooming and building occlusion work naturally.
 */
window.CampusLife = {
  attach(map) {
    const scene = window.MAIN_SCENE;
    const origin = scene.center;
    const longitudeScale = 111320 * Math.cos(origin[1] * Math.PI / 180);
    const xy = p => [(p[0] - origin[0]) * longitudeScale, (p[1] - origin[1]) * 111320];
    const lngLat = p => [origin[0] + p[0] / longitudeScale, origin[1] + p[1] / 111320];
    const area = document.querySelector('.map-area');
    const toggle = document.getElementById('campus-motion');
    const reduced = matchMedia('(prefers-reduced-motion: reduce)');
    const outerRoads = new Set(['South 4th Street', 'South 10th Street',
      'East San Fernando Street', 'East San Salvador Street']);

    // A point must stay inside the paved polygon, not on a guessed straight route.
    function inside(point, ring) {
      let hit = false;
      for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
        const a = ring[i], b = ring[j];
        if ((a[1] > point[1]) !== (b[1] > point[1]) &&
            point[0] < (b[0]-a[0]) * (point[1]-a[1]) / (b[1]-a[1]) + a[0]) hit = !hit;
      }
      return hit;
    }
    const campusRing = scene.ground.features.find(f => f.properties.kind === 'campus').geometry.coordinates[0].map(xy);
    const buildingRings = scene.geo.features.flatMap(f =>
      (f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates)
        .map(polygon => polygon[0].map(xy)));

    // Find a long safe straight section inside each polygon using its main axis.
    // Curved/short/obstructed sections are skipped instead of walking through buildings.
    function routeFor(feature, car) {
      if (feature.geometry.type !== 'Polygon') return null;
      const rings = feature.geometry.coordinates.map(r => r.map(xy));
      const ring = rings[0].slice(0, -1);
      const center = ring.reduce((a,p) => [a[0]+p[0]/ring.length,a[1]+p[1]/ring.length],[0,0]);
      let xx=0, yy=0, cross=0;
      for (const p of ring) {
        const x=p[0]-center[0], y=p[1]-center[1];
        xx+=x*x; yy+=y*y; cross+=x*y;
      }
      const angle=Math.atan2(2*cross,xx-yy)/2;
      const direction=[Math.cos(angle),Math.sin(angle)];
      const normal=[-direction[1],direction[0]];
      const projection=ring.map(p => (p[0]-center[0])*direction[0]+(p[1]-center[1])*direction[1]);
      const point = t => [center[0]+direction[0]*t,center[1]+direction[1]*t];
      let start=null, best=null;
      const low=Math.min(...projection), high=Math.max(...projection);
      for (let t=low;t<=high+2;t+=2) {
        const p=point(t);
        const safe=t<=high && [-1,0,1].every(side => {
          const q=[p[0]+normal[0]*side*(car?3.5:1.3),p[1]+normal[1]*side*(car?3.5:1.3)];
          return inside(q,rings[0]) && (car || inside(q,campusRing)) && !rings.slice(1).some(r=>inside(q,r)) &&
            !buildingRings.some(r=>inside(q,r));
        });
        if (safe && start===null) start=t;
        if (!safe && start!==null) {
          const end=t-2;
          if (!best || end-start>best.length) best={a:point(start+4),b:point(end-4),length:end-start-8,direction,normal};
          start=null;
        }
      }
      return best && best.length>(car?55:22) ? best : null;
    }
    const roads=[], paths=[];
    for (const f of scene.ground.features) {
      const car=f.properties.kind==='road' && outerRoads.has(f.properties.name);
      if (!car && f.properties.kind!=='path') continue;
      const route=routeFor(f,car);
      if (route) (car?roads:paths).push(route);
    }
    roads.sort((a,b)=>b.length-a.length);
    paths.sort((a,b)=>b.length-a.length);
    // Spread walkers around campus instead of crowding one long mall.
    const walkingRoutes=[];
    for (const route of paths) {
      if (walkingRoutes.every(r=>Math.hypot(r.a[0]-route.a[0],r.a[1]-route.a[1])>35)) walkingRoutes.push(route);
      if (walkingRoutes.length===28) break;
    }
    const actors=[];
    roads.slice(0,10).forEach((route,i)=>{
      for (const sign of [-1,1]) actors.push({route,sign,car:true,phase:(i*0.173+(sign===1?0.38:0))%1,speed:5+i%3,color:['#e5bc55','#e9eee9','#4e91ad','#b56959'][i%4]});
    });
    walkingRoutes.forEach((route,i)=>actors.push({route,sign:i%2?1:-1,car:false,phase:(i*0.137)%1,speed:0.8+(i%3)*0.15,color:i%3?'#12609a':'#d8ad43'}));

    map.addSource('campus-life',{type:'geojson',data:{type:'FeatureCollection',features:[]}});
    map.addLayer({id:'campus-life',type:'fill-extrusion',source:'campus-life',paint:{
      'fill-extrusion-color':['get','color'],'fill-extrusion-base':['get','base'],
      'fill-extrusion-height':['get','height'],'fill-extrusion-opacity':1}});

    // Each miniature is made of a few small boxes, including gold Spartan helmets.
    function draw(elapsed) {
      const features=[];
      const night=area.dataset.period==='night';
      for (const actor of actors) {
        const {route,car}=actor;
        const cycle=(actor.phase+elapsed*actor.speed/(route.length*2))%1;
        const forward=cycle<0.5;
        const distance=(forward?cycle*2:(1-cycle)*2)*route.length;
        const heading=(forward?1:-1)*actor.sign;
        const t=actor.sign===1?distance:route.length-distance;
        const lane=car?heading*2:0;
        const center=[route.a[0]+route.direction[0]*t+route.normal[0]*lane,
          route.a[1]+route.direction[1]*t+route.normal[1]*lane];
        function box(x,y,width,length,base,height,color) {
          // Keep toy-like Spartans readable at overview scale without using screen overlays.
          if (!car) { x*=1.6; y*=1.6; width*=1.6; length*=1.6; base*=1.6; height*=1.6; }
          const ring=[[-width/2,-length/2],[width/2,-length/2],[width/2,length/2],[-width/2,length/2],[-width/2,-length/2]]
            .map(p=>lngLat([center[0]+route.normal[0]*(p[0]+x)+route.direction[0]*(p[1]+y)*heading,
              center[1]+route.normal[1]*(p[0]+x)+route.direction[1]*(p[1]+y)*heading]));
          features.push({type:'Feature',properties:{color,base,height},geometry:{type:'Polygon',coordinates:[ring]}});
        }
        if(car) {
          box(0,0,2.4,5,0.2,1.2,actor.color);
          box(0,-0.3,1.9,2.6,1.2,1.9,'#314b62');
          for(const side of [-1,1]) {
            box(side*0.8,2.25,0.55,0.35,0.65,1.05,night?'#fff2bb':'#e9e8d1');
            box(side*0.8,-2.25,0.55,0.3,0.65,1,'#df5f4e');
          }
        } else {
          // Stylized miniatures use blue jerseys, gold helmets and shields.
          const step=Math.sin(elapsed*5+actor.phase*20)*0.28;
          box(-0.4,step,0.55,0.7,0,0.9,'#20384f');
          box(0.4,-step,0.55,0.7,0,0.9,'#20384f');
          box(0,0,1.25,0.8,0.85,2,actor.color);
          box(-0.85,0,0.25,1.05,0.9,1.85,'#edc765');
          box(0.8,-step,0.35,0.5,1.05,1.85,actor.color);
          box(0,0,1,0.9,2,2.8,'#edc765');
          box(0,-0.1,0.3,1.15,2.8,3.15,'#184d7a');
          box(0,0.48,0.8,0.12,2.15,2.45,'#223a50');
        }
      }
      map.getSource('campus-life').setData({type:'FeatureCollection',features});
    }
    let elapsed=0, previous=0, frame=0, visible=true, disposed=false;
    function active() { return !disposed && visible && !document.hidden && toggle.checked && !reduced.matches; }
    function tick(now) {
      if (!active()) {frame=0;previous=0;return;}
      if (!previous || now-previous>=66) {
        elapsed+=previous?Math.min((now-previous)/1000,0.15):0;
        previous=now; draw(elapsed);
      }
      frame=requestAnimationFrame(tick);
    }
    function resume() {
      if (!active()) {cancelAnimationFrame(frame);frame=0;previous=0;}
      else if (!frame) frame=requestAnimationFrame(tick);
    }
    const observer=new IntersectionObserver(entries=>{visible=entries[0].isIntersecting;resume();});
    observer.observe(map.getContainer());
    toggle.addEventListener('change',resume);
    reduced.addEventListener('change',resume);
    document.addEventListener('visibilitychange',resume);
    draw(0);resume();
    // Clean up if the map is removed, preventing background animation loops.
    map.on('remove',()=>{
      disposed=true;resume();observer.disconnect();
      toggle.removeEventListener('change',resume);
      reduced.removeEventListener('change',resume);
      document.removeEventListener('visibilitychange',resume);
    });
  }
};
