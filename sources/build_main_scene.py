"""Main-campus-only ground scene; all positions from local OSM snapshot."""
import json, math, xml.etree.ElementTree as E
from pathlib import Path
from shapely.geometry import Polygon,Point,LineString,shape,mapping
from shapely.ops import transform,unary_union
from pyproj import Transformer
P=Path(__file__).resolve().parent; W=P.parent/'web'
r=E.parse(P/'osm-main.xml').getroot()
def tags(x):return {t.attrib['k']:t.attrib['v'] for t in x.findall('tag')}
nodes={n.attrib['id']:(float(n.attrib['lon']),float(n.attrib['lat'])) for n in r.findall('node')}
ways={w.attrib['id']:w for w in r.findall('way')}
def coords(w):return [nodes[n.attrib['ref']] for n in w.findall('nd') if n.attrib['ref'] in nodes]
fwd=Transformer.from_crs(4326,32610,always_xy=True).transform
rev=Transformer.from_crs(32610,4326,always_xy=True).transform
boundary=Polygon(coords(ways['25775883'])).buffer(0)
bm=transform(fwd,boundary);context=bm.buffer(24)
allgeo=json.loads((W/'buildings.geojson').read_text(encoding='utf-8'))
features=[f for f in allgeo['features'] if boundary.covers(shape(f['geometry']).representative_point())]
occupied=unary_union([transform(fwd,shape(f['geometry'])) for f in features])
features2=[];roads=[];labels=[]
def feat(g,kind,name='',**props):
    if g.is_empty:return
    features2.append(dict(type='Feature',properties=dict(kind=kind,name=name,**props),geometry=mapping(transform(rev,g))))
feat(bm,'campus')
for wid,w in ways.items():
    t=tags(w);cs=coords(w)
    if len(cs)<2:continue
    if 'highway' in t and t.get('highway') not in ['construction','proposed'] and t.get('tunnel')!='yes' and t.get('level','0')=='0':
        line=transform(fwd,LineString(cs)); clipped=line.intersection(context)
        if clipped.is_empty:continue
        hw=t['highway'];walk=hw in ['footway','path','pedestrian','steps','cycleway'];width={'footway':2.4,'path':2,'pedestrian':8,'steps':3,'cycleway':3,'service':5,'residential':10,'tertiary':14,'secondary':16,'primary':18}.get(hw,8)
        try:width=float(t.get('width',width))
        except ValueError:pass
        g=clipped.buffer(width/2,cap_style=2,join_style=2).intersection(context)
        roads.append(g)
        feat(g,'path' if walk else 'road',t.get('name',''),osmId=wid)
        if t.get('name') in ['South 4th Street','South 10th Street','East San Fernando Street','East San Salvador Street','Paseo de San Carlos','Paseo de César E. Chávez']:
            pt=clipped.representative_point(); labels.append(dict(name=t['name'],center=list(transform(rev,pt).coords)[0]))
    if len(cs)>3 and cs[0]==cs[-1]:
        kind='green' if t.get('landuse') in ['grass','forest','meadow'] or t.get('leisure') in ['garden','park'] or t.get('natural') in ['wood','scrub','grassland'] else 'water' if t.get('leisure')=='swimming_pool' or t.get('natural')=='water' else 'plaza' if t.get('highway')=='pedestrian' and t.get('area')=='yes' else None
        if kind:
            g=transform(fwd,Polygon(cs).buffer(0)).intersection(bm).difference(occupied)
            if not g.is_empty:feat(g,kind,t.get('name',''),osmId=wid)
roadunion=unary_union(roads)
trees=0
for n in r.findall('node'):
    t=tags(n)
    if t.get('natural')!='tree':continue
    pt=transform(fwd,Point(nodes[n.attrib['id']]))
    if not bm.covers(pt) or occupied.covers(pt):continue
    # Symbol radius only; not a measured tree crown.
    feat(pt.buffer(2.2,resolution=8).difference(occupied),'tree',osmId=n.attrib['id']);trees+=1
    feat(pt.buffer(.65,resolution=6),'tree-core')
gateNodes=[nodes[n.attrib['id']] for n in r.findall('node') if tags(n).get('name')=='Boccardo Gate']
eastNodes=[nodes[n.attrib['id']] for n in r.findall('node') if tags(n).get('name')=='Lucas Gate']
gate=[sum(p[i] for p in gateNodes)/len(gateNodes) for i in [0,1]]
east=[sum(p[i] for p in eastNodes)/len(eastNodes) for i in [0,1]]
x,y=fwd(*gate);xx,yy=fwd(*east);bearing=math.degrees(math.atan2(xx-x,yy-y))
scene=dict(geo=dict(type='FeatureCollection',features=features),ground=dict(type='FeatureCollection',features=features2),boundary=mapping(boundary),bounds=[[boundary.bounds[0],boundary.bounds[1]],[boundary.bounds[2],boundary.bounds[3]]],center=list(boundary.centroid.coords)[0],gate=gate,bearing=round(bearing,2),trees=trees,groundCounts={k:sum(f['properties']['kind']==k for f in features2) for k in ['path','road','green','water','tree']},gateSource='https://www.sjsu.edu/campusmasterplan/docs/SJSU_Facility_Development_Plan_2017.pdf',gateNote='Boccardo Gateway, S. 4th Street / Paseo de San Carlos; view aligned toward Lucas Gate. SJSU has multiple entrances.')
(W/'main-scene.js').write_text('window.MAIN_SCENE='+json.dumps(scene,ensure_ascii=False,separators=(',',':'))+';',encoding='utf-8')
print('Main buildings',len(features),'bearing',scene['bearing'],'gate',gate,'layers',scene['groundCounts'])
