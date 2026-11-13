import {spawn} from 'node:child_process';
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';

export const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
export async function eventually(check, label, timeout = 30000) {
  const end = Date.now() + timeout;
  let last;
  while (Date.now() < end) {
    try { const result = await check(); if (result) return result; } catch (error) { last = error; }
    await delay(100);
  }
  throw new Error(`Timed out: ${label}${last ? ` (${last.message})` : ''}`);
}

// Native Node 24 CDP client: no additional browser automation dependency.
export async function browserSession() {
  const dir = await mkdtemp(join(tmpdir(), 'ipola-browser-'));
  const chrome = spawn(process.env.CHROME_BIN || '/usr/bin/chromium', [
    '--headless', '--no-sandbox', '--disable-dev-shm-usage', '--disable-background-networking',
    '--no-first-run', '--no-default-browser-check', '--remote-debugging-port=0',
    `--user-data-dir=${dir}`, 'about:blank'
  ], {stdio: 'ignore', detached:true});
  let ws;
  const pending = new Map();
  const listeners = new Map();
  let id = 0;
  async function close() {
    ws?.close();
    try { process.kill(-chrome.pid,'SIGTERM'); } catch (error) { if(error.code!=='ESRCH')throw error; }
    await new Promise(resolve => { if (chrome.exitCode !== null) resolve(); else { chrome.once('exit', resolve); setTimeout(resolve, 3000).unref(); } });
    await rm(dir, {recursive: true, force: true, maxRetries: 5});
  }
  try {
    const port = await eventually(async () => Number((await readFile(join(dir, 'DevToolsActivePort'), 'utf8')).split('\n')[0]), 'Chromium debugging port');
    const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
    ws = new WebSocket(pages.find(page => page.type === 'page').webSocketDebuggerUrl);
    await new Promise((resolve, reject) => { ws.addEventListener('open', resolve, {once:true}); ws.addEventListener('error', reject, {once:true}); });
    ws.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (!message.id) { for(const listener of listeners.get(message.method)||[])listener(message.params); return; }
      const entry = pending.get(message.id);
      if (!entry) return;
      clearTimeout(entry.timer); pending.delete(message.id);
      if (message.error) entry.reject(new Error(message.error.message)); else entry.resolve(message.result);
    });
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const current = ++id;
      const timer = setTimeout(() => { pending.delete(current); reject(new Error(`CDP timed out: ${method}`)); }, 15000);
      pending.set(current, {resolve, reject, timer});
      ws.send(JSON.stringify({id: current, method, params}));
    });
    const evaluate = async expression => {
      const result = await send('Runtime.evaluate', {expression, awaitPromise:true, returnByValue:true, userGesture:true});
      if (result.exceptionDetails) throw new Error(result.exceptionDetails.text + ': ' + (result.result?.description || ''));
      return result.result?.value;
    };
    await send('Page.enable');
    await send('Runtime.enable');
    await send('Emulation.setDeviceMetricsOverride', {width:390, height:844, deviceScaleFactor:1, mobile:true});
    const on=(method,listener)=>{const entries=listeners.get(method)||[];entries.push(listener);listeners.set(method,entries);};
    return {send, evaluate, on, close};
  } catch (error) { await close(); throw error; }
}
