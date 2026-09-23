"""Compile and exercise the demo in an isolated temporary copy. Python stdlib only."""
import concurrent.futures
import json
import os
import re
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
# Resolve the actual JDK binary: Windows javapath can spawn a child process,
# so terminating the launcher would leave the test server running.
settings = subprocess.run(['java','-XshowSettings:properties','-version'], capture_output=True, text=True, check=True, creationflags=FLAGS)
home = re.search(r'java.home\s*=\s*(.+)', settings.stderr).group(1).strip()
JAVA = str(Path(home)/'bin'/('java.exe' if os.name == 'nt' else 'java'))

with tempfile.TemporaryDirectory(prefix='sjsu-smoke-') as temp:
    work = Path(temp)
    shutil.copytree(ROOT/'src', work/'src')
    shutil.copytree(ROOT/'web', work/'web')
    (work/'bin').mkdir()
    subprocess.run(['javac','--release','17','-encoding','UTF-8','-d','bin','src/demo/Main.java'],cwd=work,check=True,creationflags=FLAGS)
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0))
        port=sock.getsockname()[1]
    url=f'http://127.0.0.1:{port}'
    def request(path, data=None, method=None):
        body=urllib.parse.urlencode(data).encode() if data is not None else None
        req=urllib.request.Request(url+path,data=body,method=method)
        try:
            with urllib.request.urlopen(req,timeout=5) as response:
                return response.status,response.read().decode(),response.headers
        except urllib.error.HTTPError as e:
            return e.code,e.read().decode(),e.headers
    def start():
        p=subprocess.Popen([JAVA,'-cp','bin','demo.Main',str(port)],cwd=work,stdout=subprocess.PIPE,stderr=subprocess.PIPE,creationflags=FLAGS)
        for _ in range(50):
            if p.poll() is not None:raise RuntimeError(p.stderr.read().decode())
            try:
                if request('/')[0]==200:return p
            except (OSError,urllib.error.URLError):pass
            time.sleep(.1)
        p.terminate();p.wait(timeout=5);raise RuntimeError('Server did not start')
    process=start()
    try:
        code,html,_=request('/')
        assert code==200 and 'lang="en"' in html
        assert 'https://unpkg.com' not in html
        for path in ['/app.js','/floor-data.js','/style.css','/main-scene.js','/campus-data.js','/vendor/maplibre-gl.js','/vendor/maplibre-gl.css','/sources.html']:
            assert request(path)[0]==200,path
        assert request('/coverage.json')[2].get_content_type()=='application/json'
        assert request('/buildings.geojson')[2].get_content_type()=='application/geo+json'
        assert request('/%2e%2e/README.md')[0]==404
        assert request('/api/bookings',method='DELETE')[0]==405
        assert json.loads(request('/api/bookings')[1])==[]
        base=dict(room='ENG-101',date='2026-10-13',start='14:00',end='15:00',name='Test "name"',purpose='Study <group> & planning')
        assert request('/api/bookings',base)[0]==201
        assert request('/api/bookings',base)[0]==409
        assert request('/api/bookings',dict(base,start='15:00',end='16:00'))[0]==201
        assert request('/api/bookings',dict(base,start='09:30',end='10:00'))[0]==409
        for delta in [dict(room='FAKE-101'),dict(end='13:00'),dict(start='07:00'),dict(date='2026-02-30'),dict(name=''),dict(purpose='bad\tvalue'),dict(start='9:00')]:
            assert request('/api/bookings',dict(base,**delta))[0]==400,delta
        concurrent_booking=dict(base,room='ENG-201')
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(lambda _:request('/api/bookings',concurrent_booking)[0],range(2)))
        assert sorted(results)==[201,409],results
        saved=json.loads(request('/api/bookings')[1])
        assert len(saved)==3 and saved[0]['purpose']==base['purpose'] and saved[0]['name']==base['name']
        process.terminate();process.wait(timeout=5)
        process=start()
        assert json.loads(request('/api/bookings')[1])==saved
        assert request('/api/bookings',base)[0]==409
        print('PASS: Java 17 compilation, offline assets, MIME types, path containment, input validation, booking conflicts, adjacent slots, concurrent requests, JSON escaping, and restart persistence.')
    finally:
        process.terminate();process.wait(timeout=5)

