import assert from 'node:assert/strict';
import {stripVTControlCharacters} from 'node:util';
import {spawn} from 'node:child_process';
import {mkdir, mkdtemp, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
import {createServer} from 'node:net';
import {browserSession, eventually} from './browser-session.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const temp = await mkdtemp(join(tmpdir(), 'ipola-smoke-'));
const apiOrigin = 'http://127.0.0.1:18080';
const origin = 'http://127.0.0.1:15173';
const email = 'mobile-test@example.com';
const password = 'synthetic-test-password';
const children = [];
let browser;
const startedAt=new Date().toISOString();
let passed=false;
async function start(command, args, options = {}) {
  const {readyPattern,...spawnOptions}=options;
  const child = spawn(command, args, {cwd:root, env:process.env, stdio:['ignore','pipe','pipe'], detached:true, ...spawnOptions});
  const state={child,ready:false,error:null};
  let output='';
  for(const stream of [child.stdout,child.stderr])stream.on('data',chunk=>{
    output=(output+chunk.toString()).slice(-16384);
    // GitHub Actions enables ANSI styling, including between Local and its colon.
    if(readyPattern?.test(stripVTControlCharacters(output)))state.ready=true;
  });
  child.on('error', error => { state.error=error; });
  children.push(child); return state;
}
async function requireFreePort(port) {
  const server=createServer();
  await new Promise((resolve,reject)=>{
    server.once('error',()=>reject(new Error(`Refusing to seed fixtures: port ${port} is occupied or unavailable`)));
    server.listen(port,'127.0.0.1',resolve);
  });
  await new Promise((resolve,reject)=>server.close(error=>error?reject(error):resolve()));
}
function running(state) {
  if(state.error)throw state.error;
  if(state.child.exitCode!==null||state.child.signalCode!==null)throw new Error('Isolated test service exited during startup');
  return state.ready;
}
async function api(path, body, token, method = 'POST') {
  const response = await fetch(apiOrigin + '/api' + path, {method, headers:{'Content-Type':'application/json', ...(token?{Authorization:`Bearer ${token}`}:{})}, ...(body?{body:JSON.stringify(body)}:{})});
  assert.ok(response.ok, `Fixture request ${path}: ${response.status}`);
  return response.status === 204 ? null : response.json();
}
try {
  await mkdir(join(root,'artifacts'),{recursive:true});
  await rm(join(root,'artifacts/mobile-smoke.png'),{force:true});
  await writeFile(join(root,'artifacts/mobile-smoke.json'),JSON.stringify({status:'running',startedAt})+'\n');
  await requireFreePort(18080);await requireFreePort(15173);
  const backend=await start(process.env.JAVA_HOME ? join(process.env.JAVA_HOME,'bin/java') : 'java', [
    '-jar', 'target/ipola-backend-0.1.0-SNAPSHOT.jar', '--server.port=18080',
    '--spring.datasource.url=jdbc:h2:mem:mobile;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1',
    '--ipola.registration.open=true', `--ipola.photos.directory=${join(temp,'photos')}`
  ], {cwd:join(root,'backend'),readyPattern:/Started IpolaApplication/});
  await eventually(async () => running(backend)&&(await fetch(apiOrigin+'/api/animals')).ok, 'isolated backend startup');
  await api('/auth/register',{email,nickname:'手機測試照顧者',password});
  await api('/auth/register',{email:'other-mobile@example.com',nickname:'其他測試會員',password});
  const login = await api('/auth/login',{email,password});
  const fixture = {animalId:null,kind:'SIGHTING',observedAt:'2026-10-01T10:00:00Z',latitude:25.033123,longitude:121.565432,notes:'fixture-private-note'};
  await api('/observations',fixture,login.accessToken);
  const frontend=await start('npm',['run','dev','--','--port','15173'], {cwd:join(root,'frontend'),env:{...process.env,IPOLA_API_TARGET:apiOrigin},readyPattern:/Local:/});
  await eventually(async () => running(frontend)&&(await fetch(origin)).ok, 'frontend startup');
  browser = await browserSession();
  const {evaluate:e,send} = browser;
  let activeAuthorization;
  browser.on('Network.requestWillBeSent', ({request}) => {
    // Ephemeral test token only; never write headers or tokens into evidence.
    const authorization=request.headers.Authorization||request.headers.authorization;
    if(request.url.startsWith(origin+'/api/') && authorization)activeAuthorization=authorization;
  });
  await send('Network.enable');
  const wait = (expression,label) => eventually(()=>e(expression),label);
  const click = selector => e(`document.querySelector(${JSON.stringify(selector)})?.click()`);
  const button = (text,within='document') => e(`[...${within}.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(text)})?.click()`);
  const fill = (selector,value) => e(`(()=>{const n=document.querySelector(${JSON.stringify(selector)});if(!n)throw Error('Missing input');n.value=${JSON.stringify(value)};n.dispatchEvent(new Event('input',{bubbles:true}));n.dispatchEvent(new Event('change',{bubbles:true}));})()`);
  const body = () => e('document.body.innerText');
  const submit = selector => e(`document.querySelector(${JSON.stringify(selector)}).requestSubmit()`);
  await send('Page.navigate',{url:origin});
  await wait(`document.querySelector('[data-record-id]')`,'guest records');
  assert.equal((await body()).includes('fixture-private-note'),false);
  assert.equal((await body()).includes('25.033123'),false);
  await wait(`document.querySelector('.leaflet-marker-icon')`,'map marker without tiles');
  await send('Browser.setPermission',{permission:{name:'geolocation'},setting:'denied',origin});
  await button('登入');
  await wait(`document.querySelector('#auth-form')`,'login form');
  await fill('#email',email); await fill('#password','wrong-test-password'); await submit('#auth-form');
  await wait(`document.querySelector('#status').innerText.includes('錯誤')`,'wrong password feedback');
  await fill('#password',password); await submit('#auth-form');
  await wait(`document.querySelector('#identity').innerText.includes('手機測試照顧者')`,'successful login');
  async function create(kind,note) {
    await click('#new-record'); await wait(`document.querySelector('#record-form')`,'record form');
    await click('#locate');
    await wait(`document.querySelector('#location-status').innerText.includes('手動')`,'denied geolocation fallback');
    await fill('#kind',kind);await fill('#observedAt','2026-10-01T12:00');
    await fill('#latitude','25.033123');await fill('#longitude','121.565432');await fill('#notes',note);
    await submit('#record-form');
    await wait(`document.querySelector('#status').innerText.includes('紀錄已新增')`,'new record confirmation');
    await wait(`document.querySelector('#record-list')?.innerText.includes(${JSON.stringify(note)})`,'record in own history');
  }
  await create('SIGHTING','mobile-sighting-private');
  await create('FEEDING','mobile-feeding-private');
  assert.ok((await body()).includes('餵養紀錄'));
  const id = await e(`[...document.querySelectorAll('[data-record-id]')].find(c=>c.innerText.includes('mobile-sighting-private')).dataset.recordId`);
  const card = `document.querySelector('[data-record-id="${id}"]')`;
  await button('修改紀錄',card);await wait(`document.querySelector('#record-form')`,'edit form');
  await fill('#notes','mobile-edited-private');await submit('#record-form');
  await wait(`document.querySelector('#record-list')?.innerText.includes('mobile-edited-private')`,'edited record');
  await button('上傳／取代照片',card);await wait(`document.querySelector('#photo-form')`,'photo form');
  await e(`(()=>{const canvas=document.createElement('canvas');canvas.width=32;canvas.height=24;canvas.getContext('2d').fillRect(0,0,32,24);const bytes=Uint8Array.from(atob(canvas.toDataURL('image/png').split(',')[1]),c=>c.charCodeAt(0));const dt=new DataTransfer();dt.items.add(new File([bytes],'synthetic.png',{type:'image/png'}));document.querySelector('#photo-file').files=dt.files;document.querySelector('#photo-consent').checked=true;})()`);
  await submit('#photo-form');await wait(`document.querySelector('#status').innerText.includes('照片已上傳')`,'photo upload');
  await button('查看私人照片',card);
  await wait(`${card}?.querySelector('img')?.naturalWidth>0`,'authenticated private photo rendered');
  for (const width of [360,390,1280]) {
    await send('Emulation.setDeviceMetricsOverride',{width,height:844,deviceScaleFactor:1,mobile:width<500});
    assert.ok(await e('document.documentElement.scrollWidth<=window.innerWidth'),`No horizontal overflow at ${width}px`);
  }
  await mkdir(join(root,'artifacts'),{recursive:true});
  await send('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  const screenshot=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});
  await writeFile(join(root,'artifacts/mobile-smoke.png'),Buffer.from(screenshot.data,'base64'));
  await button('登出');await wait(`!document.querySelector('#identity').innerText.includes('手機測試照顧者')`,'logout');
  assert.equal((await body()).includes('mobile-edited-private'),false);
  assert.equal(await e(`document.querySelector('img.photo')!==null`),false);
  async function loginAs(address) {
    await button('登入');await wait(`document.querySelector('#auth-form')`,'login form');
    await fill('#email',address);await fill('#password',password);await submit('#auth-form');
    await wait(`document.querySelector('#record-list')`,'logged in records');
  }
  await loginAs('other-mobile@example.com');
  assert.equal((await body()).includes('mobile-edited-private'),false);
  assert.ok((await body()).includes('25.033123'),'other member retains approved precise coordinates');
  assert.equal(await e(`${card}?.innerText.includes('修改紀錄')`),false);
  await button('登出');await loginAs(email);await click('#my-records');
  await wait(`${card}?.innerText.includes('mobile-edited-private')`,'owner history restored');
  await e('window.confirm=()=>true');
  await button('移除照片',card);await wait(`document.querySelector('#status').innerText.includes('照片已移除')`,'remove photo');
  await button('刪除紀錄',card);await wait(`!${card}`,'delete record');
  // Fill only synthetic fixtures to exercise UI pagination against the actual API.
  for(let i=0;i<11;i++)await api('/observations',{...fixture,notes:`pagination-${i}`},login.accessToken);
  await click('#my-records');await wait(`document.querySelector('#next')?.disabled===false`,'next page available');
  const firstIds=await e(`[...document.querySelectorAll('[data-record-id]')].map(n=>n.dataset.recordId)`);
  await click('#next');await wait(`document.querySelector('.page-controls')?.innerText.includes('第 2 頁')`,'second page');
  const secondIds=await e(`[...document.querySelectorAll('[data-record-id]')].map(n=>n.dataset.recordId)`);
  assert.ok(secondIds.length>0 && secondIds.every(id=>!firstIds.includes(id)),'pages do not repeat records');
  assert.equal(await e('localStorage.length+sessionStorage.length'),0,'no token persistence');
  assert.ok(activeAuthorization,'authenticated browser request observed');
  await api('/auth/logout',null,activeAuthorization.slice(7));
  activeAuthorization=undefined;
  await click('#browse');
  await wait(`document.querySelector('#status')?.innerText.includes('登入已失效')`,'revoked token clears private session');
  assert.equal((await body()).includes('25.033123'),false);
  assert.equal((await body()).includes('fixture-private-note'),false);
  const report={status:'passed',startedAt,completedAt:new Date().toISOString(),widths:[360,390,1280],checks:['guest privacy','wrong-password feedback','login','map markers','denied location/manual coordinates','sighting','feeding','own history','edit','private photo upload/read/delete','other-member privacy','logout clearing','delete record','pagination','no persistent token','revoked token clears private session'],data:'synthetic only',scope:'isolated H2 and local Vite; no external tiles or public deployment'};
  await writeFile(join(root,'artifacts/mobile-smoke.json'),JSON.stringify(report,null,2)+'\n');
  passed=true;
  console.log('Mobile browser smoke passed:',report.checks.length,'checks; synthetic data only.');
} finally {
  if(!passed)await writeFile(join(root,'artifacts/mobile-smoke.json'),JSON.stringify({status:'failed',startedAt,completedAt:new Date().toISOString(),data:'synthetic only'})+'\n');
  await browser?.close();
  for(const child of children.reverse()) {
    try { process.kill(-child.pid,'SIGTERM'); } catch (error) { if(error.code!=='ESRCH')throw error; }
  }
  await rm(temp,{recursive:true,force:true,maxRetries:5});
}
