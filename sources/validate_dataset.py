import json
from pathlib import Path
from shapely.geometry import shape
root=Path(__file__).resolve().parent.parent
fc=json.loads((root/'web/buildings.geojson').read_text(encoding='utf-8'))
meta=json.loads((root/'web/coverage.json').read_text(encoding='utf-8'))
ids=set()
for f in fc['features']:
    p=f['properties'];g=shape(f['geometry'])
    assert g.is_valid and not g.is_empty,p['name']
    assert p['id'] not in ids,p['id']
    ids.add(p['id'])
    assert p['area']>0 and p['length']>=p['width']>0
    assert p['sourceUrl'].startswith('https://www.openstreetmap.org/')
    if p['height'] is None:assert p['renderHeight']==.5 and p['heightSource']=='unknown'
    else:assert p['renderHeight']==p['height'] and 0<p['height']<150
    assert not p['rooms'] or p['id'] in ['ENG','SCI','CL','MLK']
assert meta['buildingCount']==len(ids)
assert meta['heightCount']==sum(f['properties']['height'] is not None for f in fc['features'])
assert sum(r['count'] for r in meta['regions'])==len(ids)
for r in meta['registry']:
    assert all(x in ids for x in r['matched'])
    assert r['matched'] or r in meta['unmatched']
assert len(meta['regions'])==5
print('PASS: geometry validity, unique IDs, coverage, dimensions, height provenance, and demo-room scope.')
