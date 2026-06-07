import urllib.request, urllib.error, json

BASE = 'http://backend:8080'

def get(path, token=None):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]

def post(path, body, token=None):
    data = json.dumps(body).encode()
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:400]

OK = "\033[32mOK\033[0m"
FAIL = "\033[31mFAIL\033[0m"

def check(label, status, expected=200):
    icon = OK if status == expected else FAIL
    return icon, label, status

# ── Logins ───────────────────────────────────────────────────────────────────
st, r  = post('/api/v1/auth/login', {'username': 'admin',      'password': 'admin123'})
ADMIN  = r.get('accessToken') if isinstance(r, dict) else None
ic, lb, st2 = check('admin login', st)
print(f"  {ic}  [{st2}] {lb}")

st, r2 = post('/api/v1/auth/login', {'username': 'directeur',  'password': 'Directeur2024!'})
DIR    = r2.get('accessToken') if isinstance(r2, dict) else None
ic, lb, st2 = check('directeur login', st)
print(f"  {ic}  [{st2}] {lb}  imfCode={r2.get('imfCode') if isinstance(r2,dict) else '?'}")

st, r3 = post('/api/v1/auth/login', {'username': 'agent_mvogo','password': 'Agent2024!'})
AGT    = r3.get('accessToken') if isinstance(r3, dict) else None
ic, lb, st2 = check('agent login', st)
print(f"  {ic}  [{st2}] {lb}")

st, r4 = post('/api/v1/auth/login', {'username': 'analyste1',  'password': 'Analyst2024!'})
ANA    = r4.get('accessToken') if isinstance(r4, dict) else None
ic, lb, st2 = check('analyste login', st)
print(f"  {ic}  [{st2}] {lb}")

print()

# ── Endpoint public ──────────────────────────────────────────────────────────
st, r = get('/api/v1/ping')
ic, lb, st2 = check('/ping (public)', st)
print(f"  {ic}  [{st2}] {lb}  => {r}")

print()

# ── Admin ────────────────────────────────────────────────────────────────────
st, r = get('/api/v1/admin/imf', ADMIN)
ic, lb, st2 = check('/admin/imf', st)
nb = len(r.get('data', [])) if isinstance(r, dict) else '?'
print(f"  {ic}  [{st2}] {lb}  => {nb} IMF(s)")

st, r = get('/api/v1/admin/users?page=0&size=10', ADMIN)
ic, lb, st2 = check('/admin/users', st)
total = r.get('data', {}).get('total', '?') if isinstance(r, dict) else '?'
print(f"  {ic}  [{st2}] {lb}  => total={total}")

st, r = get('/api/v1/admin/agences?page=0&size=5', ADMIN)
ic, lb, st2 = check('/admin/agences', st)
print(f"  {ic}  [{st2}] {lb}  => {str(r)[:100]}")

print()

# ── Agents ───────────────────────────────────────────────────────────────────
st, r = get('/api/v1/agents', DIR)
ic, lb, st2 = check('/agents', st)
nb = len(r.get('data', {}).get('content', [])) if isinstance(r, dict) else '?'
print(f"  {ic}  [{st2}] {lb}  => {nb} agent(s)")

print()

# ── Collectes épargne ────────────────────────────────────────────────────────
st, r = get('/api/v1/collectes-epargne?page=0&size=5', DIR)
ic, lb, st2 = check('/collectes-epargne', st)
if isinstance(r, dict):
    d = r.get('data', {})
    total = d.get('totalElements', d.get('total', '?'))
    first = d.get('content', [{}])[0] if d.get('content') else {}
    print(f"  {ic}  [{st2}] {lb}  total={total}  first.client={first.get('clientIdExterne','?')}  montant={first.get('montantCollecte','?')}")
else:
    print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

# Collectes avec filtre statut
st, r = get('/api/v1/collectes-epargne?statut=VALIDEE&page=0&size=20', DIR)
ic, lb, st2 = check('/collectes-epargne?statut=VALIDEE', st)
if isinstance(r, dict):
    d = r.get('data', {})
    total = d.get('totalElements', d.get('total', '?'))
    print(f"  {ic}  [{st2}] {lb}  total={total}")
else:
    print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

print()

# ── Clients informels ────────────────────────────────────────────────────────
st, r = get('/api/v1/clients?page=0&size=10', DIR)
ic, lb, st2 = check('/clients', st)
if isinstance(r, dict):
    d = r.get('data', {})
    total = d.get('totalElements', d.get('total', '?'))
    first = d.get('content', [{}])[0] if d.get('content') else {}
    print(f"  {ic}  [{st2}] {lb}  total={total}  first={first.get('nomComplet','?')}")
else:
    print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

print()

# ── Créances ─────────────────────────────────────────────────────────────────
st, r = get('/api/v1/creances?page=0&size=5', DIR)
ic, lb, st2 = check('/creances', st)
if isinstance(r, dict):
    d = r.get('data', {})
    total = d.get('totalElements', d.get('total', '?'))
    print(f"  {ic}  [{st2}] {lb}  total={total}")
else:
    print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

print()

# ── KPI ──────────────────────────────────────────────────────────────────────
st, r = get('/api/v1/kpi/dashboard-summary', DIR)
ic, lb, st2 = check('/kpi/dashboard-summary', st)
print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

st, r = get('/api/v1/kpi/par-stats?dateDebut=2026-01-01&dateFin=2026-05-26', DIR)
ic, lb, st2 = check('/kpi/par-stats', st)
print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

st, r = get('/api/v1/kpi/collecte-stats?dateDebut=2026-01-01&dateFin=2026-05-26', DIR)
ic, lb, st2 = check('/kpi/collecte-stats', st)
print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

print()

# ── ML Scoring ───────────────────────────────────────────────────────────────
st, r = get('/api/v1/ml/model/health', ANA)
ic, lb, st2 = check('/ml/model/health (analyste)', st)
print(f"  {ic}  [{st2}] {lb}  => {str(r)[:200]}")

st, r = get('/api/v1/ml/alertes', DIR)
ic, lb, st2 = check('/ml/alertes', st)
print(f"  {ic}  [{st2}] {lb}  => {str(r)[:100]}")

print()
print("Tests terminés.")
