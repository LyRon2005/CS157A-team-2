"""Rebuild audited map data from downloaded public snapshots. Requires shapely/pyproj."""
import json, re, math, xml.etree.ElementTree as ET
from pathlib import Path
from collections import Counter
from shapely.geometry import Polygon, Point, LineString, shape, mapping
from shapely.ops import unary_union, polygonize, transform
from pyproj import Transformer

ROOT=Path(__file__).resolve().parent
WEB=ROOT.parent/'web'
project=Transformer.from_crs(4326,32610,always_xy=True).transform
def tags(e): return {t.attrib['k']:t.attrib['v'] for t in e.findall('tag')}
def clean(g):
    g=g.buffer(0)
    return g if g.geom_type in ('Polygon','MultiPolygon') and not g.is_empty else None
def official_poly(v):
    s=v.get('shape') or {}
    if not isinstance(s,dict) or s.get('type')!='polygon': return None
    pts=s.get('paths',[])
    if pts and len(pts[0])==2 and isinstance(pts[0][0],(int,float)):
        return clean(Polygon([(x[1],x[0]) for x in pts]))
    return None
def number(s):
    try: return float(s)
    except (ValueError,TypeError): return None

osm=[];boundaries=[]
for f in ROOT.glob('osm-*.xml'):
    r=ET.parse(f).getroot();nodes={n.attrib['id']:(float(n.attrib['lon']),float(n.attrib['lat'])) for n in r.findall('node')}
    ways={w.attrib['id']:w for w in r.findall('way')};used=set()
    def coords(w):return [nodes[n.attrib['ref']] for n in w.findall('nd') if n.attrib['ref'] in nodes]
    for rel in r.findall('relation'):
        t=tags(rel)
        if not ('building' in t or t.get('amenity')=='university'):continue
        outer=[];inner=[];members=[]
        for m in rel.findall('member'):
            if m.attrib['type']!='way' or m.attrib['ref'] not in ways:continue
            cs=coords(ways[m.attrib['ref']])
            if len(cs)>2:(inner if m.attrib.get('role')=='inner' else outer).append(LineString(cs));members.append(m.attrib['ref'])
        if not outer:continue
        g=unary_union(list(polygonize(unary_union(outer))))
        if inner:g=g.difference(unary_union(list(polygonize(unary_union(inner)))))
        g=clean(g)
        if g is None:continue
        if 'building' in t:
            osm.append(dict(id='r'+rel.attrib['id'],tags=t,g=g,url='https://www.openstreetmap.org/relation/'+rel.attrib['id']));used.update(members)
        else:boundaries.append((t,g))
    for wid,w in ways.items():
        t=tags(w);cs=coords(w)
        if len(cs)<4 or cs[0]!=cs[-1]:continue
        if not ('building' in t or t.get('amenity')=='university'):continue
        g=clean(Polygon(cs))
        if g is None:continue
        if 'building' in t and t['building'] not in ('no','construction') and wid not in used:osm.append(dict(id='w'+wid,tags=t,g=g,url='https://www.openstreetmap.org/way/'+wid))
        elif t.get('amenity')=='university':boundaries.append((t,g))
osm=list({b['id']:b for b in osm}.values())
for b in osm:b['m']=transform(project,b['g'])
city=[]
for f in ROOT.glob('city-*.geojson'):
    for v in json.loads(f.read_text())['features']:
        g=clean(shape(v['geometry']))
        h=number(v['properties'].get('BUILDINGHEIGHT'))
        if g is not None and h and 0<h<400:city.append((transform(project,g),round(h*1200/3937,2),v['properties']['OBJECTID']))

raw=json.loads((ROOT/'official-inventory.json').read_text(encoding='utf-8'))
# Keep public outdoor facilities; indoor rooms are not separate buildings.
official={}
for v in raw:
    if v.get('level')!=[0] or v['name'].startswith(('ParkSJ','Downtown Farmers','Student Services and Global')):continue
    key=(v['name'],round(v['lat'],5),round(v['lng'],5))
    if key not in official:official[key]=v
    else:official[key]['categories']=sorted(set(official[key]['categories']+v['categories']))

mainbounds=[g for t,g in boundaries if 'San José State University Main Campus' in t.get('name','')]
campus=unary_union(mainbounds)
selected={};registry=[]
site_words=['Field','Courts','Tennis Complex','Golf Complex','Community Garden','MLML Norte Facility','MLML Operations','MLML Aquaculture','MLML Main Lab','CEFCU Stadium']
for v in official.values():
    pt=Point(v['lng'],v['lat']);pm=transform(project,pt);op=official_poly(v);om=transform(project,op) if op is not None else None
    is_site=any(s in v['name'] for s in site_words) and v['name'] not in ['Fieldhouse','MLML Aquaculture Laboratory','MLML Main Laboratory','Sports Field Facility']
    candidates=[]
    for b in osm:
        if b['m'].distance(pm)>100 and (om is None or not b['m'].intersects(om)):continue
        overlap=b['m'].intersection(om).area/b['m'].area if om is not None else 0
        if is_site:
            if om is not None and overlap>.7:candidates.append((overlap,b))
        elif b['g'].covers(pt) or overlap>.5:
            candidates.append((2 if b['g'].covers(pt) else overlap,b))
    if not candidates and not is_site:
        nearest=sorted(((b['m'].distance(pm),b) for b in osm),key=lambda x:x[0])
        if nearest and nearest[0][0]<18:candidates=[(0,nearest[0][1])]
    chosen=[b for _,b in sorted(candidates,key=lambda x:-x[0])] if is_site else ([max(candidates,key=lambda x:x[0])[1]] if candidates else [])
    for b in chosen:
        rec=selected.setdefault(b['id'],dict(b,official=[]))
        if not any(x['name']==v['name'] for x in rec['official']):rec['official'].append(v)
    registry.append(dict(name=v['name'],officialId=v['id'],center=[v['lng'],v['lat']],kind='site' if is_site else 'building',matched=[b['id'] for b in chosen],url='https://map.sjsu.edu/?id=2189#!m/'+str(v['id'])))

# Include every mapped structure inside the OSM university outline, and structures
# explicitly attributed to SJSU in OSM. Never include a whole rectangular city area.
for b in osm:
    t=b['tags'];university=any(s in (t.get('operator','')+' '+t.get('name','')) for s in ['San José State','San Jose State','Spartan Athletics'])
    inside=not campus.is_empty and campus.covers(b['g'].representative_point())
    if inside or university:selected.setdefault(b['id'],dict(b,official=[]))

def region(x,y):
    if y<37:return 'moss'
    if x< -121.92:return 'timpany'
    if x> -121.84:return 'aviation'
    if y<37.327:return 'south'
    return 'main'

features=[]
for bid,b in selected.items():
    t=b['tags'];g=b['g'];m=b['m'];p=g.representative_point();names=[v['name'] for v in b['official']]
    name=names[0] if names else t.get('name','Ancillary structure · OSM '+bid)
    if names and t.get('name') and any(s in names[0] for s in ['MLML','CEFCU']):name=t['name']
    h=number(t.get('height'));hs='OSM height tag' if h else 'unknown';hu=b['url'] if h else None
    matches=[]
    for cg,ch,cid in city:
        if not m.intersects(cg):continue
        i=m.intersection(cg).area;score=i/m.union(cg).area
        if score>.72:matches.append((score,ch,cid))
    # Do not transfer a historical survey height onto known post-2006 construction.
    newer=any(s in name for s in ['Campus Village 2','Interdisciplinary Science','Habbas Law','Recreation and Aquatic','South Campus Parking','Student Wellness'])
    if not h and matches and not newer:
        score,h,cid=max(matches);hs='City GIS · metadata LiDAR 2006';hu='https://geo.sanjoseca.gov/server/rest/services/DOT/DOT_Basemap_InfrastructureMaintenance/MapServer/13'
    lengths=[];rect=list(m.minimum_rotated_rectangle.exterior.coords)
    for a,z in zip(rect,rect[1:]):lengths.append(math.dist(a,z))
    ident={'Engineering':'ENG','Science Building':'SCI','Clark Hall':'CL','Dr. Martin Luther King, Jr. Library':'MLK'}.get(name,bid.upper())
    props=dict(id=ident,osmId=bid,name=name,aliases=names,campus=region(p.x,p.y),center=[p.x,p.y],height=round(h,2) if h else None,renderHeight=round(h,2) if h else .5,heightSource=hs,heightUrl=hu,levels=t.get('building:levels'),area=round(m.area,1),length=round(max(lengths),1),width=round(min(lengths),1),sourceUrl=b['url'],officialUrl='https://map.sjsu.edu/?id=2189#!m/'+str(b['official'][0]['id']) if b['official'] else None,geometrySource='OpenStreetMap',named=bool(names or t.get('name')),category='housing' if t.get('building') in ['residential','dormitory','apartments'] or any(z in name for z in ['Village','Washburn','Joe West']) else 'parking' if 'Garage' in name else 'academic',rooms=[])
    # Only original demo room groups have simulated bookings. No invented rooms
    # for garages, residential buildings or other facilities.
    if ident in ['ENG','SCI','CL','MLK']:props['rooms']=[ident+'-'+str(n) for n in [101,201,301]]
    features.append(dict(type='Feature',properties=props,geometry=mapping(g)))
features.sort(key=lambda f:f['properties']['name'])
idmap={f['properties']['osmId']:f['properties']['id'] for f in features}
for r in registry:r['matched']=[idmap[x] for x in r['matched']];r['campus']=region(*r['center'])
regions=[]
for key,label in [('main','Main Campus & Downtown'),('south','South Campus'),('moss','Moss Landing'),('timpany','Timpany Center'),('aviation','Aviation & Technology')]:
    fs=[f for f in features if f['properties']['campus']==key];rs=[r for r in registry if r['campus']==key]
    if not fs and not rs:continue
    gs=[shape(f['geometry']) for f in fs]+[Point(r['center']) for r in rs]
    bounds=unary_union(gs).bounds
    regions.append(dict(id=key,name=label,bounds=[[bounds[0]-.0005,bounds[1]-.0005],[bounds[2]+.0005,bounds[3]+.0005]],count=len(fs)))
fc=dict(type='FeatureCollection',features=features)
meta=dict(retrieved='2026-09-20 UTC',buildingCount=len(features),heightCount=sum(f['properties']['height'] is not None for f in features),registryCount=len(registry),unmatched=[r for r in registry if not r['matched']],regions=regions,registry=registry,notes=['OSM footprints are mapped outlines, not a certified as-built survey.','LiDAR heights date to 2006; OSM height tags may also derive from historical sources.','Unknown heights are drawn as 0.5 m footprint plates, not estimated building heights.','Official directory contains facilities, outdoor sites and aliases; it is not a complete property register.'])
(WEB/'campus-data.js').write_text('window.CAMPUS_DATA='+json.dumps(dict(geo=fc,meta=meta),ensure_ascii=False,separators=(',',':'))+';\n',encoding='utf-8')
(WEB/'buildings.geojson').write_text(json.dumps(fc,ensure_ascii=False),encoding='utf-8')
(WEB/'coverage.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2),encoding='utf-8')
print('BUILDINGS',len(features),'HEIGHTS',meta['heightCount'],'REGISTRY',len(registry),'UNMATCHED',len(meta['unmatched']))
print('REGIONS',[(r['name'],r['count']) for r in regions])
for r in meta['unmatched']:print('MISSING',r['officialId'],r['name'])
for f in features:
    p=f['properties'];print(p['id'],p['name'],p['height'],p['heightSource'])
