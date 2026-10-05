#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Exercise actual isolated HTTP/MySQL workflows using TEST records and private random credentials."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
from datetime import datetime,timezone
from zoneinfo import ZoneInfo
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8130').rstrip('/');checks=0

def check(ok,message):
    """Count real acceptance checks without printing credentials or business responses."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

def key():return str(uuid.uuid4())

class Client:
    """Use an isolated session cookie jar and the application's real CSRF endpoint."""
    def __init__(self,name,password):
        """Actual isolated acceptance operation. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        """Actual isolated acceptance operation. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,path+': wrong error')
        return value

def detail(id):return admin.request(f'/trials/{id}')
def record(id):return detail(id)['record']
def row(kind,id,trial):return record(id) if kind=='trials' else next(r for r in detail(trial)[kind] if r['id']==id)
def command(who,kind,id,action,trial=None,extra=None,status=200,code=None,body=None):
    """Bind one explicit action to a fresh version and stable UUID. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    value=body or {'requestKey':key(),'version':row(kind,id,trial)['version'],'note':'TEST 人工核实记录'}
    value.update(extra or {});return who.request(f'/{kind}/{id}/commands/{action}','POST',value,status,code)
def capture():
    """Capture stable private responses for restart and backup restoration. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard']
    for kind in ['sites','trials']:
        paths.append('/'+kind+'?size=100&sort=oldest')
        paths.extend(f'/{kind}/{r["id"]}' for r in admin.request('/'+kind+'?size=100')['content'])
    return {p:admin.request(p) for p in paths}
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--capture',action='store_true');args=parser.parse_args()
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
    state=json.loads(STATE.read_text());responses=capture()
    if args.capture:
        state['responses']=responses;STATE.write_text(json.dumps(state,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(responses),'result':'PASS'}));raise SystemExit
    for path,expected in state['responses'].items():check(responses[path]==expected,'Persistence mismatch: '+path)
    for name,username in state['users'].items():check(Client(username,state['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
    replay_actor=Client(state['users']['designer'],state['password'])
    for request in state.get('replays',[]):check(replay_actor.request(request['path'],'POST',request['body'])==request['response'],'Replay mismatch')
    print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(responses),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable isolated database.')
if STATE.exists():raise SystemExit('QA state exists; use --verify or a new isolated database.')
check(admin.request('/trials')['total']==0 and admin.request('/sites')['total']==0,'Business tables not empty on first boot')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};userIds={};replays=[];today=datetime.now(ZoneInfo('Asia/Shanghai')).date().isoformat()
roles={r['name']:r['id'] for r in admin.request('/admin/roles')};dep=admin.request('/admin/departments','POST',{'name':'TEST 田间试验 '+suffix})['id'];outdep=admin.request('/admin/departments','POST',{'name':'TEST 外部试验 '+suffix})['id']
allObserver=admin.request('/admin/roles','POST',{'name':'TEST 观测员ALL '+suffix,'scope':'ALL','permissions':['trial.read','plot.observe','dashboard','export']})['id']
selfDesigner=admin.request('/admin/roles','POST',{'name':'TEST 本人方案 '+suffix,'scope':'SELF','permissions':['site.read','site.write','trial.read','trial.write','plot.assign','dashboard','export']})['id']
for name,role,department,label in [('designer',roles['试验设计'],dep,'试验设计'),('review',roles['独立复核'],dep,'独立复核'),('observer',roles['田间观测'],dep,'田间观测'),('other',allObserver,dep,'观测ALL'),('outside',roles['试验设计'],outdep,'外部设计'),('self',selfDesigner,dep,'本人设计')]:
    username='test-'+name+'-'+suffix;users[name]=username;userIds[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+label,'password':password,'roleId':role,'departmentId':department,'enabled':True})['id'];clients[name]=Client(username,password)
designer,review,observer,other=[clients[n] for n in ['designer','review','observer','other']]
site=designer.request('/sites','POST',{'requestKey':key(),'reference':'TEST-SITE-'+suffix,'name':'TEST 品种对比田块','departmentId':dep,'enabled':True});trials=[]
def draft(complete=True):
    """Create explicitly labelled disposable protocol records. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    t=designer.request('/trials','POST',{'requestKey':key(),'reference':'TEST-TRIAL-'+key()[:8],'name':'TEST 田间品种对比','siteId':site['id'],'reviewerId':userIds['review'],'cropType':'CEREAL','cropName':'TEST 谷物','objective':'TEST 两品种株高观测记录，不作农业推荐或显著性判断','blockCount':4,'plotArea':'12.5000'});trials.append(t['id'])
    if complete:
        for code,control in [('VAR-A',True),('VAR-B',False)]:designer.request('/treatments','POST',{'requestKey':key(),'trialId':t['id'],'code':code,'name':'TEST 品种 '+code,'description':'TEST 品种识别与观测处理','control':control,'enabled':True})
        designer.request('/measures','POST',{'requestKey':key(),'trialId':t['id'],'code':'HEIGHT','name':'TEST 株高','unit':'cm','minimum':'0.0000','maximum':'300.0000','required':True,'enabled':True})
    return t['id']
def allocate(id):
    """Actual isolated acceptance operation. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    command(designer,'trials',id,'submit');command(review,'trials',id,'approve');return command(designer,'trials',id,'allocate')
def prepare(id):
    """Every observer personally receives their own plot before start. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    for i,p in enumerate(detail(id)['plots']):
        actor=observer if i%2==0 else other;name='observer' if i%2==0 else 'other'
        command(designer,'plots',p['id'],'assign',id,{'observerId':userIds[name]});command(actor,'plots',p['id'],'receive',id)
    command(designer,'trials',id,'start',extra={'date':today})
    for p in detail(id)['plots']:command(observer if p['observerId']==userIds['observer'] else other,'plots',p['id'],'plant',id,{'date':today})
def observation(id,p,value=100,missing='',base=None,accepted=True):
    """Missing is null with a reason; corrections reference the prior accepted id. https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2."""
    actor=observer if p['observerId']==userIds['observer'] else other;m=detail(id)['measures'][0]
    o=actor.request('/observations','POST',{'requestKey':key(),'plotId':p['id'],'measureId':m['id'],'value':value,'missingReason':missing,'note':'TEST 实际观测／修订记录','observedDate':today,'supersedesId':base})
    if accepted:command(actor,'observations',o['id'],'submit',id);command(review,'observations',o['id'],'accept',id)
    return o['id']
# Complete main workflow, exact retries, frozen definitions, independently accepted missingness and append-only correction.
t1=draft();command(designer,'trials',t1,'submit');command(admin,'trials',t1,'approve',status=403,code='ASSIGNED_REVIEWER_REQUIRED');command(review,'trials',t1,'reject');command(designer,'trials',t1,'submit');command(designer,'trials',t1,'withdraw');command(designer,'trials',t1,'submit');command(review,'trials',t1,'approve')
body={'requestKey':key(),'version':record(t1)['version'],'note':'TEST 一次随机布局'};path=f'/trials/{t1}/commands/allocate';allocated=designer.request(path,'POST',body);check(designer.request(path,'POST',body)==allocated,'Retry response differs');replays.append({'path':path,'body':body,'response':allocated});designer.request(path,'POST',{**body,'note':'TEST changed'},409,'REQUEST_KEY_REUSED');command(designer,'trials',t1,'allocate',status=409,code='INVALID_STATE')
plots=detail(t1)['plots'];check(len(plots)==8,'Wrong plot count');check(len(allocated['layoutSeed'])==64 and len(allocated['layoutHash'])==64 and len(allocated['planHash'])==64,'Missing layout proofs')
for b in range(1,5):check({p['treatmentCode'] for p in plots if p['blockIndex']==b}=={'VAR-A','VAR-B'},'Incomplete randomized block')
r=detail(t1)['treatments'][0];designer.request('/treatments/'+str(r['id']),'PUT',{**r,'requestKey':key(),'name':'TEST forbidden edit'},409,'INVALID_STATE');command(designer,'trials',t1,'start',extra={'date':today},status=409,code='CREW_NOT_READY');prepare(t1);command(designer,'trials',t1,'end',status=409,code='MISSING_REQUIRED_OBSERVATION')
ps=detail(t1)['plots'];o0=observation(t1,ps[0],0);o1=observation(t1,ps[1],None,'TEST 植株缺失');check(row('observations',o1,t1)['value'] is None,'Missing changed to zero')
# Pending revision retains old accepted value and blocks finalization.
new=observation(t1,ps[0],10,base=o0,accepted=False);check(next(x for x in detail(t1)['observations'] if x['id']==o0)['value']==0,'Original value changed');command(designer,'trials',t1,'end',status=409,code='PENDING_OBSERVATIONS');command(observer,'observations',new,'submit',t1);command(observer,'observations',new,'accept',t1,status=403,code='FORBIDDEN');command(review,'observations',new,'reject',t1);command(observer,'observations',new,'submit',t1);command(review,'observations',new,'accept',t1)
for p in ps[2:]:observation(t1,p,100+p['blockIndex'])
# A plot is excluded without deleting its accepted observation; rejected request is retained too.
p=ps[-1];actor=observer if p['observerId']==userIds['observer'] else other;command(actor,'plots',p['id'],'request-exclusion',t1);command(review,'plots',p['id'],'reject-exclusion',t1);command(actor,'plots',p['id'],'request-exclusion',t1);command(designer,'trials',t1,'end',status=409,code='PENDING_EXCLUSIONS');command(review,'plots',p['id'],'approve-exclusion',t1)
s=detail(t1)['summary'];check(sum(r['n'] for r in s)==6 and sum(r['missing'] for r in s)==1 and sum(r['excluded'] for r in s)==1,'Descriptive counts incorrect');check(len(detail(t1)['observations'])==9,'Revision or excluded facts deleted')
command(designer,'trials',t1,'end');command(review,'trials',t1,'reopen');command(designer,'trials',t1,'end');command(review,'trials',t1,'close');check(record(t1)['outcome']=='FINISHED' and len(record(t1)['dataHash'])==64,'Final freeze absent')
# Distinct abort, cancel, rejected draft and GUI-ready live trial.
t2=draft();allocate(t2);prepare(t2);command(designer,'trials',t2,'abort');command(review,'trials',t2,'close');check(record(t2)['outcome']=='ABORTED','Abort outcome changed');t3=draft(False);command(designer,'trials',t3,'submit',status=409,code='INCOMPLETE_TREATMENTS');command(designer,'trials',t3,'cancel');t4=draft();command(designer,'trials',t4,'submit');command(review,'trials',t4,'reject');t5=draft();allocate(t5);prepare(t5)
uiPlots=detail(t5)['plots'];uiPlot=next(p for p in uiPlots if p['observerId']==userIds['observer']);uiObs=observation(t5,uiPlot,99,accepted=False)
for p in uiPlots:
    if p['id']!=uiPlot['id']:observation(t5,p,100)
# ALL-only observer sees only assigned plots in page, JSON, dashboard and CSV authorization.
check(len(other.request(f'/trials/{t1}')['plots'])==4,'ALL observer leaked plots');check(len(other.request(f'/trials/{t1}/report.json')['plots'])==4,'JSON scope leaked');other.request('/admin/users',status=403,code='FORBIDDEN');other.request('/audit',status=403,code='FORBIDDEN');other.request('/sites',status=403,code='FORBIDDEN');check('passwordHash' not in json.dumps(other.request('/options')),'Hash leaked in options');other.request(f'/plots/{ps[0]["id"]}/commands/receive','POST',{'requestKey':key(),'version':ps[0]['version'],'note':'TEST'},403,'OUT_OF_SCOPE')
for name in ['outside','self']:
    clients[name].request(f'/trials/{t1}',status=403,code='OUT_OF_SCOPE');clients[name].request(f'/trials/{t1}/report.json',status=403,code='OUT_OF_SCOPE');check(clients[name].request('/trials?size=100')['total']==0,'Scope leak '+name)
# Range/date/precision, idempotent stale rollback, real CSRF, administrative reference and session gates.
m=detail(t5)['measures'][0];p=next(p for p in uiPlots if p['id']!=uiPlot['id']);actor=observer if p['observerId']==userIds['observer'] else other;base=next(o for o in detail(t5)['observations'] if o['plotId']==p['id'])['id'];v={'requestKey':key(),'plotId':p['id'],'measureId':m['id'],'value':'1.23456','missingReason':'','note':'TEST 精度','observedDate':today,'supersedesId':base};actor.request('/observations','POST',v,400,'INVALID_DECIMAL');v['value']='301';actor.request('/observations','POST',v,409,'OUTSIDE_MEASURE_RANGE');v['value']='10';v['missingReason']='TEST missing';actor.request('/observations','POST',v,409,'VALUE_AND_MISSING_REASON');v['missingReason']='';v['observedDate']='1999-01-01';actor.request('/observations','POST',v,400,'INVALID_DATE')
designer.request('/trials?size=101',status=400,code='INVALID_INPUT');designer.request('/trials?sort=sql',status=400,code='INVALID_INPUT');designer.request('/trials','POST',{},403,csrf=False);admin.request('/admin/departments/'+str(dep),'DELETE',{},409,'CONFLICT');check(len(admin.request('/admin/permissions'))==12 and len(admin.request('/admin/menus'))==11,'Catalog mismatch');check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Hash leaked')
a=next(a for a in admin.request('/admin/users') if a['id']==userIds['outside']);admin.request('/admin/users/'+str(a['id']),'PUT',{**a,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(a['id']),'PUT',{**a,'enabled':True})
STATE.parent.mkdir(exist_ok=True);state={'password':password,'users':users,'userIds':userIds,'departmentId':dep,'siteId':site['id'],'trialIds':trials,'uiTrialId':t5,'uiTrialReference':record(t5)['reference'],'uiObservationId':uiObs,'draftTrialId':t4,'replays':replays};state['responses']=capture()
fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'trials':len(trials),'plots':sum(len(detail(i)['plots']) for i in trials),'result':'PASS'}))
