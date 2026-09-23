// Presentation-only floor choices. Original research remains in FLOOR_DATA.
window.DEMO_FLOORS = Object.fromEntries(window.MAIN_SCENE.geo.features.map(({properties:b})=>{
 const evidence=window.FLOOR_DATA[b.id];
 const mapped=Number(b.levels);
 const reported=evidence?.count;
 const count=reported||(Number.isInteger(mapped)&&mapped>0?mapped:1);
 const basis=reported?'Published count':mapped>0?'Estimated · map level tag':'Estimated · one-floor placeholder';
 return [b.id,{count,levels:Array.from({length:count},(_,i)=>i+1),estimated:!reported,
  summary:reported?`${evidence.summary||`${count} floors reported`} · Demo levels`:`${count} ${count===1?'floor':'floors'} · ${basis}`,
  note:(evidence?.note||'No verified floor count is available.')+' '+
   (reported?'Floor labels are demo ordinals unless separately documented.':'This floor count is a demo assumption, not a verified building measurement.')+
   ' Floor bands are schematic equal-height divisions of the mapped exterior; they do not represent interior plans, wing setbacks or measured floor elevations.',
  source:evidence?.source||b.sourceUrl}];
}));
