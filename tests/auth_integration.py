"""End-to-end tests for the local three-tier app.
Run: python tests/auth_integration.py "E:/apache-tomcat-11.0.25"
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
    subprocess.run(['javac','--release','17','-encoding','UTF-8','-cp',str(TOMCAT/'lib/servlet-api.jar'),'-d',str(classes),*sources,str(ROOT/'tests/DatabaseAssertions.java')], check=True, creationflags=FLAGS)
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
    process = None
    try:
        java('sjsuclassrooms.config.CreateTables')
        process = start()
        assert json.loads(request('/api/auth/me')[1]) == {'user': None}
        for asset in ['/', '/js/auth.js', '/css/auth.css', '/js/campus-map.js', '/data/main-scene.js', '/data/building-directory.js', '/vendor/maplibre-gl.js']:
            assert request(asset)[0] == 200, asset
        for forbidden in ['/../config/local.properties', '/%2e%2e/config/local.properties', '/api/bookings']:
            assert request(forbidden)[0] == 404
        assert request('/api/auth/login')[0] == 405
        valid = dict(studentId='001234567', firstName='Test', lastName='Student', email='Integration@SJSU.EDU', password=' Strong pass123 ', confirmPassword=' Strong pass123 ')
        assert request('/api/auth/signup', valid, {'Origin': 'https://untrusted.example'})[0] == 403
        assert request('/api/auth/signup', valid, {'X-Requested-With': ''})[0] == 403
        # Try invalid input through the API, without using any browser-side checks.
        invalid_cases = [
            {'studentId': ''}, {'studentId': '123'}, {'studentId': '0012345678'},
            {'studentId': 'abcdefghi'}, {'studentId': '１２３４５６７８９'}, {'studentId': '001 23456'},
            {'email': 'x@sjsu.edu.evil'}, {'email': 'x@othersjsu.edu'}, {'email': 'x@@sjsu.edu'},
            {'email': '.x@sjsu.edu'}, {'email': 'x.@sjsu.edu'}, {'email': 'x..y@sjsu.edu'},
            {'email': 'x y@sjsu.edu'}, {'email': 'a'*65+'@sjsu.edu'}, {'email': '@sjsu.edu'},
            {'firstName': ' '}, {'firstName': '<script>alert(1)</script>'}, {'lastName': '123'},
            {'email': '\x00x@sjsu.edu'}, {'firstName': 'Name\n'}, {'firstName': 'A'*101}, {'firstName': 'A\x00B'}, {'firstName': 'A\u200bB'},
            {'lastName': '---'}, {'firstName': '\u0301Name'},
            {'password': 'short', 'confirmPassword': 'short'},
            {'password': 'x'*129, 'confirmPassword': 'x'*129},
            {'password': ' '*8, 'confirmPassword': ' '*8},
            {'password': '\u00a0'*8, 'confirmPassword': '\u00a0'*8},
            {'confirmPassword': 'mismatch'}, {'role': 'admin'}]
        for index, changes in enumerate(invalid_cases):
            if index and index % 12 == 0:
                process.terminate(); process.wait(timeout=5); process = start()
            status, text, _ = request('/api/auth/signup', {**valid, **changes})
            assert status == 400 and 'error' in json.loads(text), (changes.keys(), status, text)
        # Also test broken form encoding and repeated keys that ordinary forms do not generate.
        for body in [b'', b'email=%ZZ', b'email=%C3%28', b'email=%FF', b'email=%',
                     b'studentId=001234567&studentId=001234568', b'email=a&', b'x=' + b'a'*8200]:
            assert request('/api/auth/signup', body)[0] == 400
        assert request('/api/auth/signup', b'{}', {'Content-Type': 'application/json'})[0] == 415
        for missing in valid:
            assert request('/api/auth/signup', {k: v for k, v in valid.items() if k != missing})[0] == 400
        process.terminate(); process.wait(timeout=5); process = start()
        # International names and ASCII email normalization are accepted.
        valid['firstName'] = '  Nguye\u0302\u0303n  '
        valid['lastName'] = "O’Neill"
        status, text, headers = request('/api/auth/signup', valid)
        assert status == 201, (status, text)
        account = json.loads(text)
        assert account['user']['firstName'] == 'Nguyễn' and account['user']['lastName'] == 'O’Neill'
        assert account['user']['studentId'] == '001234567' and account['user']['email'] == 'integration@sjsu.edu'
        assert 'password' not in text.lower() and 'pbkdf2' not in text
        cookie = headers.get('Set-Cookie'); assert 'HttpOnly' in cookie and 'SameSite=Strict' in cookie
        assert request('/api/auth/logout', {})[0] == 403
        csrf = account['csrfToken']
        assert request('/api/auth/signup', valid)[0] == 409
        assert request('/api/auth/signup', {**valid, 'studentId': '001234568'})[0] == 409
        assert request('/api/auth/logout', {}, {'X-CSRF-Token': csrf})[0] == 200
        assert json.loads(request('/api/auth/me')[1])['user'] is None
        replay = urllib.request.build_opener()
        assert json.loads(request('/api/auth/me', headers={'Cookie': cookie.split(';')[0]}, client=replay)[1])['user'] is None
        assert request('/api/auth/login', dict(studentId='001234567', password='wrongpass'))[0] == 401
        assert request('/api/auth/login', dict(studentId="' OR 1=1--", password='wrongpass'))[0] == 400
        login = dict(studentId='001234567', password=valid['password'])
        status, text, hdr = request('/api/auth/login', login)
        assert status == 200 and hdr.get('Set-Cookie') != cookie
        assert json.loads(request('/api/auth/me')[1])['user']['studentId'] == '001234567'
        # Database unique constraint must also handle simultaneous signups, not just sequential checks.
        race = {**valid, 'studentId': '002345678', 'email': 'race@sjsu.edu'}
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            codes = list(pool.map(lambda _: request('/api/auth/signup', race, client=urllib.request.build_opener())[0], range(2)))
        assert sorted(codes) == [201, 409], codes
        java('sjsuclassrooms.config.CreateTables')  # Re-running setup must preserve existing accounts.
        java('sjsuclassrooms.tests.DatabaseAssertions')
        # Restart Tomcat: account data should remain, but the old login session should end.
        process.terminate(); process.wait(timeout=5)
        process = start()
        assert json.loads(request('/api/auth/me')[1])['user'] is None
        assert request('/api/auth/login', login)[0] == 200
        # Boundary values remain valid, rather than being silently truncated.
        for index, length in enumerate([8, 128]):
            boundary = dict(studentId=f'00345678{index}', firstName='A'*100, lastName='Lê',
                            email=('a'*63+str(index))+'@sjsu.edu',
                            password='p'*(length-1)+'!', confirmPassword='p'*(length-1)+'!')
            assert request('/api/auth/signup', boundary)[0] == 201
            assert request('/api/auth/login', {k: boundary[k] for k in ['studentId','password']})[0] == 200
        # A bounded per-IP request limit applies to authentication work.
        for _ in range(30):
            code = request('/api/auth/login', {'studentId': 'bad', 'password': '12345678'})[0]
            if code == 429: break
        assert code == 429
        print('PASS: missing/unknown/duplicate fields, malformed UTF-8, international names, email/password/name boundaries, signup/login/logout, leading-zero IDs, email rules, duplicate race, CSRF/origin, cookies, SQL injection rejection, account persistence, session invalidation, rate limiting and map assets.')
    # Always stop the test server and drop only the guarded test database.
    finally:
        if process and process.poll() is None: process.terminate(); process.wait(timeout=5)
        java('sjsuclassrooms.tests.DatabaseAssertions', 'drop')
