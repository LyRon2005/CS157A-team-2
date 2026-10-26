"""End-to-end tests for the local three-tier app.
Run: python tests/review_integration.py "E:/apache-tomcat-11.0.25"
Requires JDK, Tomcat, Connector/J and the private local MySQL settings.
Use a new temporary Tomcat folder and a random test database name.
The normal application database and its accounts are not test fixtures.
No third-party Python packages are needed. A failed assertion means a test failed.
"""
import concurrent.futures
import http.cookiejar
import json
import os
import re
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

# Find files relative to this test script, not the terminal working directory.
ROOT = Path(__file__).resolve().parents[1]
TOMCAT = Path(sys.argv[1] if len(sys.argv) > 1 else os.environ.get('CATALINA_HOME', '')) .resolve()
assert (TOMCAT/'lib/servlet-api.jar').is_file(), 'Supply the Tomcat 11 installation folder.'
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
settings = subprocess.run(['java', '-XshowSettings:properties', '-version'], capture_output=True, text=True, check=True, creationflags=FLAGS)
java_home = re.search(r'java.home\s*=\s*(.+)', settings.stderr).group(1).strip()
JAVA = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
# Override only the database name so test records never enter sjsu_classrooms.
env = dict(os.environ, DB_NAME='sjsu_auth_test_' + uuid.uuid4().hex[:12])
# Create a temporary deployment and remove it when the test finishes.
with tempfile.TemporaryDirectory(prefix='sjsu-tomcat-test-') as folder:
    work = Path(folder)
    shutil.copytree(TOMCAT/'conf', work/'conf')
    for name in ['logs', 'temp', 'work']: (work/name).mkdir()
    for name in ['config','database']: shutil.copytree(ROOT/name, work/name)
    app = work/'webapps/SJSUClassrooms'
    shutil.copytree(ROOT/'src/main/webapp', app)
    classes = app/'WEB-INF/classes'; classes.mkdir()
    sources = [str(p) for p in (ROOT/'src/main/java').rglob('*.java')]
    subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(TOMCAT/'lib/servlet-api.jar'),'-d',str(classes),*sources,str(ROOT/'tests/DatabaseAssertions.java'),str(ROOT/'tests/ReviewAssertions.java')], check=True, creationflags=FLAGS)
    cp = str(classes) + os.pathsep + str(app/'WEB-INF/lib/*')
    def java(main, *args):
        return subprocess.run([JAVA,'-cp',cp,main,*args],cwd=work,env=env,check=True,creationflags=FLAGS)
    # Ask the OS for an available port so the running demo can stay online.
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0)); port=sock.getsockname()[1]
    (work/'conf/server.xml').write_text(f'<Server port="-1"><Service name="Catalina"><Connector address="127.0.0.1" port="{port}" protocol="HTTP/1.1"/><Engine name="Catalina" defaultHost="localhost"><Host name="localhost" appBase="webapps" autoDeploy="false"/></Engine></Service></Server>')
    origin=f'http://127.0.0.1:{port}'
    url=origin+'/SJSUClassrooms'
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    # Send a real HTTP request. The cookie jar acts like a browser login session.
    def request(path, data=None, headers=None, method=None, client=None):
        body = data if isinstance(data, bytes) else urllib.parse.urlencode(data).encode() if data is not None else None
        h = {'Origin': origin, 'X-Requested-With': 'SJSUClassrooms'}
        if body is not None: h['Content-Type'] = 'application/x-www-form-urlencoded'
        h.update(headers or {})
        req = urllib.request.Request(url + path, body, h, method=method)
        try: response = (client or opener).open(req, timeout=15)
        except urllib.error.HTTPError as e: response = e
        with response: return response.status, response.read().decode(), response.headers
    # Start a real Tomcat process and wait for /me to respond before testing.
    def start():
        log = open(work/'logs/test-console.log', 'ab')
        proc = subprocess.Popen([JAVA, '-Dcatalina.home='+str(TOMCAT), '-Dcatalina.base='+str(work),
            '-Dsjsu.config='+str(work/'config/local.properties'), '-Djava.io.tmpdir='+str(work/'temp'),
            '-cp', str(TOMCAT/'bin/bootstrap.jar')+os.pathsep+str(TOMCAT/'bin/tomcat-juli.jar'),
            'org.apache.catalina.startup.Bootstrap', 'start'], cwd=work, env=env, stdout=log, stderr=log, creationflags=FLAGS)
        log.close()
        for _ in range(150):
            try:
                if request('/api/auth/me')[0] == 200: return proc
            except OSError: pass
            if proc.poll() is not None: break
            time.sleep(.2)
        if proc.poll() is None: proc.terminate(); proc.wait(timeout=10)
        raise RuntimeError((work/'logs/test-console.log').read_text(errors='replace')[-6000:])

    process=None
    try:
        java('sjsuclassrooms.config.CreateTables')
        process=start()
        guest=urllib.request.build_opener()
        def signup(student,email,client):
            status,text,_=request('/api/auth/signup',dict(studentId=student,firstName='Review',lastName='Tester',email=email,password='Review test123!',confirmPassword='Review test123!'),client=client)
            assert status==201,(status,text)
            return json.loads(text)['csrfToken']
        token=signup('001234567','reviewone@sjsu.edu',opener)
        other=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        otherToken=signup('001234568','reviewtwo@sjsu.edu',other)
        def post(data,client=opener,csrf=token):
            return request('/api/reviews',dict(buildingId='22',**data),{'X-CSRF-Token':csrf},client=client)
        assert request('/api/reviews?buildingId=22',client=guest)[0]==200
        # Review access is independent of teaching-building exploration.
        for target in [1,7,18,35,41,46,57]:
            assert request('/api/reviews?buildingId='+str(target),client=guest)[0]==200
        assert request('/api/reviews',dict(buildingId=46,writtenReview='Map structure review'),{'X-CSRF-Token':token})[0]==201
        assert post(dict(writtenReview='guest'),guest)[0]==401
        assert post(dict(writtenReview='csrf'),csrf='')[0]==403
        for value in ['', ' ', '\u00a0', 'a'*1001, 'bad\x00text']:
            assert post(dict(writtenReview=value))[0]==400
        original="Quiet room. <script>alert(1)</script> O'Neill"
        status,text,_=post(dict(writtenReview=original))
        assert status==201,(status,text)
        review=json.loads(text)['reviewId']
        public=json.loads(request('/api/reviews?buildingId=22',client=guest)[1])['reviews'][0]
        assert public['text']==original and not public['mine']
        assert set(public)=={'id','author','text','created','mine'}
        for action in ['update','delete']:
            assert post(dict(action=action,reviewId=review,writtenReview='not mine'),other,otherToken)[0]==404
        assert post(dict(action='update',reviewId=review,writtenReview='Updated review'))[0]==200
        assert post(dict(action='update',reviewId=review,writtenReview='Updated review'))[0]==200
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results=list(pool.map(lambda i:post(dict(writtenReview='Concurrent '+str(i))),range(22)))
        assert all(r[0]==201 for r in results),results
        ids=[json.loads(r[1])['reviewId'] for r in results]
        assert len(set(ids))==22
        page=json.loads(request('/api/reviews?buildingId=22')[1])
        assert len(page['reviews'])==20 and page['hasMore']
        tail=json.loads(request('/api/reviews?buildingId=22&offset=20')[1])
        assert len(tail['reviews'])==3 and not tail['hasMore']
        assert json.loads(request('/api/reviews?buildingId=22&sort=oldest')[1])['reviews'][0]['id']==review
        assert request('/api/reviews?buildingId=999999')[0]==404
        assert request('/api/reviews?buildingId=22&sort=bad')[0]==400
        java('sjsuclassrooms.config.CreateTables')
        assert len(json.loads(request('/api/reviews?buildingId=22&offset=20')[1])['reviews'])==3
        assert post(dict(action='delete',reviewId=review))[0]==200
        assert post(dict(action='delete',reviewId=review))[0]==404
        print('PASS: review reading, validation, CSRF, ownership, edit/delete, concurrent IDs, pagination, sorting and repeated setup.',flush=True)
        java('sjsuclassrooms.tests.ReviewAssertions')
        if os.environ.get('REVIEW_UI_TEST'):
            print('UI_URL='+url,flush=True)
            print('UI_STOP_FILE='+str(work/'finish-ui'),flush=True)
            while not (work/'finish-ui').exists(): time.sleep(1)
    finally:
        if process and process.poll() is None: process.terminate(); process.wait(timeout=5)
        java('sjsuclassrooms.tests.DatabaseAssertions','drop')
