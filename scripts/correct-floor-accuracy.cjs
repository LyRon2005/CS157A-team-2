const fs=require('fs'),vm=require('vm');
const c={window:{}};vm.createContext(c);vm.runInContext(fs.readFileSync('web/floor-data.js','utf8'),c);const d=c.window.FLOOR_DATA;
for(const r of Object.values(d)){r.levels=[];if(r.count&&!r.summary)r.summary=`${r.count} stories reported in source`;}
Object.assign(d.ENG,{status:'documented-levels',source:'https://gcp-web.sjsu.edu/engineering/docs/FA24%20Engineering%20Buildings.pdf',summary:'Floors 1–4 documented · Fall 2024',levels:[1,2,3,4],note:'SJSU Engineering floor maps explicitly label the first, second, third and fourth floors. Floor elevations and room availability are not modeled.',reviewed:'2026-09-23'});
for(const [id,year] of Object.entries({W25774742:'2022–23',W594034896:'2020',W25774755:'2020',R10631685:'2021'}))d[id].summary=`${d[id].count} stories reported · ${year} housing publication`;
d.MLK.summary='8 above-ground floors reported; lower level excluded';d.W25774741.summary='8 stories reported; underground parking excluded';
fs.writeFileSync('web/floor-data.js','// Published counts do not establish level labels or measured geometry.\nwindow.FLOOR_DATA = '+JSON.stringify(d,null,2)+';\n');
let s=fs.readFileSync('web/app.js','utf8');
s=s.replace(/function floorFeatures\(b,selected=0\)\{[^\n]+/, 'function floorFeatures(){return empty;}');
s=s.replace(/ \$\('floor-message'\)\.textContent=[^\n]+/," $('floor-message').textContent=f?.levels?.length?'Choose a documented floor to preview.':'Floor selection awaits verified level labels.';");
s=s.replace("f.note+(f.count?' Floor numbers and equally spaced bands are illustrative, not verified level names, measured elevations or interior plans.':'')","f.note+' Floor bands are unavailable because measured floor elevations are not documented.'");
s=s.replace('if(f?.count&&!f.infoOnly)for(let n=1;n<=f.count;n++)','for(const n of f?.levels||[])');
fs.writeFileSync('web/app.js',s);
const audit=JSON.parse(fs.readFileSync('sources/floor-research.json','utf8'));audit.reviewed='2026-09-23';for(const b of audit.buildings)if(d[b.id])Object.assign(b,d[b.id]);fs.writeFileSync('sources/floor-research.json',JSON.stringify(audit,null,2)+'\n');
for(const file of ['README.md','FLOOR_RESEARCH.md','web/sources.html']){
 let t=fs.readFileSync(file,'utf8').replace('22 from a 2015 assessment','21 from a 2015 assessment').replace('22 explicitly labeled historical counts','21 explicitly labeled historical counts').replace('22 use the March','21 use the March').replace('and 8 use separate','and 9 use separate');
 t=t.replace('Floor bands use equal spacing for illustration, not measured elevations or interior plans.','Floor bands are not drawn without measured elevations. Only Engineering has enabled floor labels (1–4), documented in the Fall 2024 maps.');
 if(file!=='web/sources.html')t+='\nAccuracy correction (September 23, 2026): published counts alone no longer generate floor buttons or equal-height 3D bands. Only Engineering has enabled level labels (1–4), documented in official Fall 2024 floor maps. Other buildings retain published evidence while level selection awaits verification. There are 21 assessment-based records and 9 counts with separate documentation. Source dates do not certify current conditions. This supersedes earlier descriptions of illustrative floor bands.\n';
 t=t.replace(/Where both count and building height are available,[\s\S]*?Unknown exterior heights/, 'Measured floor elevations are unavailable, so the exterior is not divided into floor bands. Unknown exterior heights');
 t=t.replace('Floor buttons are ordinal preview labels, not verified room-directory identifiers. Equal-height bands are illustrative.', 'Floor buttons require explicitly documented level labels. No equal-height bands are generated.');
 t=t.replace(/\| Engineering \| ENG \|[^\n]+/, '| Engineering | ENG | Floors 1–4 documented · Fall 2024 | documented-levels | [Official floor maps](https://gcp-web.sjsu.edu/engineering/docs/FA24%20Engineering%20Buildings.pdf) |');
 fs.writeFileSync(file,t);
}
let html=fs.readFileSync('web/index.html','utf8').replace('Confirm to zoom in and explore its floors.','Confirm to zoom in and view available building information.');fs.writeFileSync('web/index.html',html);
