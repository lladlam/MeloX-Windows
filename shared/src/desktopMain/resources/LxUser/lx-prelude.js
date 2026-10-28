/* LX user-script host. Only the bound request/crypto functions cross into Java. */
(() => {
  'use strict';
  const native = globalThis.__lxNative;
  delete globalThis.__lxNative;
  const callbacks = new Map();
  const timers = new Map();
  const handlers = [];
  const events = [];
  let nextRequest = 1, nextTimer = 1, initialized = false, alerted = false;
  let pending = null, settled = null;
  const supportedSources = new Set(['kw', 'kg', 'tx', 'wy', 'mg', 'local']);
  const qualities = ['128k', '320k', 'flac', 'flac24bit'];
  const actions = ['musicUrl', 'lyric', 'pic'];
  const declarations = {};
  const json = value => JSON.stringify(value, (_key, v) =>
    ArrayBuffer.isView(v) ? Array.from(v) : v);
  const crypto = (op, ...args) => JSON.parse(native.crypto(op, json(args)));
  const console = {};
  ['log','info','debug','warn','error','group','groupEnd','table','time','timeEnd'].forEach(name => {
    console[name] = () => {}; // User-source text can contain credentials; keep it out of logs.
  });
  globalThis.console = console;
  globalThis.setTimeout = (fn, delay, ...args) => {
    if (typeof fn !== 'function') throw new TypeError('Timer callback must be a function');
    const id = nextTimer++;
    const ms = Number(delay) || 0;
    timers.set(id, {at: Date.now() + Math.max(0, ms), fn, args});
    return id;
  };
  globalThis.clearTimeout = id => timers.delete(id);
  const lx = {
    version: '2.0.0', env: 'mobile', currentScriptInfo: {},
    EVENT_NAMES: Object.freeze({request: 'request', inited: 'inited', updateAlert: 'updateAlert'}),
    request(url, options, callback) {
      const id = nextRequest++;
      if (typeof callback !== 'function') return () => {};
      callbacks.set(id, callback);
      try { native.request(id, String(url), json(options || {})); }
      catch (error) { callbacks.delete(id); throw error; }
      return () => { callbacks.delete(id); native.cancel(id); };
    },
    on(event, handler) {
      if (event !== 'request') return Promise.reject(new Error('The event is not supported: ' + event));
      if (typeof handler === 'function') handlers.push(handler);
      return Promise.resolve();
    },
    send(event, payload) {
      if (event === 'inited') {
        if (initialized) return Promise.reject(new Error('Script is inited'));
        initialized = true;
        const sources = payload && payload.sources || {};
        Object.entries(sources).forEach(([source, value]) => {
          if (!supportedSources.has(source) || !value || value.type !== 'music') return;
          const q = qualities.filter(item => Array.isArray(value.qualitys) && value.qualitys.includes(item));
          const a = actions.filter(item => Array.isArray(value.actions) && value.actions.includes(item));
          if (q.length || a.length) declarations[source] = {qualitys: q, actions: a};
        });
        events.push({name: 'inited', value: null});
        return Promise.resolve();
      }
      if (event === 'updateAlert') {
        if (alerted) return Promise.reject(new Error('The update alert can only be called once.'));
        if (!payload || !payload.log) return Promise.reject(new Error('log is required.'));
        alerted = true;
        let updateUrl = typeof payload.updateUrl === 'string' ? payload.updateUrl : null;
        if (updateUrl && !/^https?:\/\/[^\s$.?#].[^\s]*$/.test(updateUrl) && updateUrl.length > 1024) updateUrl = null;
        events.push({name: 'updateAlert', value: {log: String(payload.log).slice(0, 1024), updateUrl}});
        return Promise.resolve();
      }
      return Promise.reject(new Error('The event is not supported: ' + event));
    },
    utils: {
      crypto: {
        md5: value => crypto('md5', value),
        randomBytes: size => new Uint8Array(crypto('randomBytes', size)),
        aesEncrypt: (data, mode, key, iv) => new Uint8Array(crypto('aesEncrypt', data, mode, key, iv)),
        rsaEncrypt: (data, key) => new Uint8Array(crypto('rsaEncrypt', data, key)),
      },
      buffer: {
        from: (value, encoding) => new Uint8Array(crypto('from', value, encoding)),
        bufToString: (value, encoding) => crypto('toString', value, encoding),
      },
    },
  };
  Object.defineProperty(globalThis, 'lx', {value: lx, writable: false, configurable: false});
  globalThis.module = {exports: {}};
  globalThis.exports = globalThis.module.exports;
  globalThis.__lxInfo = info => Object.assign(lx.currentScriptInfo, info);
  globalThis.__lxDeliver = (id, error, response, body) => {
    const callback = callbacks.get(id);
    if (!callback) return;
    callbacks.delete(id);
    callback(error === null ? null : new Error(error), response, body);
  };
  globalThis.__lxTick = now => {
    for (const [id, timer] of timers) if (timer.at <= now) {
      timers.delete(id); timer.fn(...timer.args);
    }
  };
  globalThis.__lxReady = () => handlers.length > 0 || typeof globalThis.musicUrl === 'function' ||
    typeof module.exports.musicUrl === 'function';
  globalThis.__lxDeclarations = () => JSON.stringify(declarations);
  globalThis.__lxEvents = () => JSON.stringify(events);
  globalThis.__lxStart = (action, args) => {
    if (pending) throw new Error('An LX action is already in progress');
    settled = null;
    const source = args.source || 'kw';
    const info = {type: args.type || '128k', musicInfo: args.musicInfo || args};
    try {
      const result = handlers.length ? handlers[0]({source, action, info}) :
        (globalThis.musicUrl || module.exports.musicUrl)(info.musicInfo, info.type);
      pending = Promise.resolve(result).then(value => {
        settled = {done: true, value: value === undefined ? null : value}; pending = null;
      }, error => { settled = {done: true, error: String(error && error.message || error)}; pending = null; });
    } catch (error) { settled = {done: true, error: String(error && error.message || error)}; }
  };
  globalThis.__lxResult = () => json(settled || {done: false});
  // No Java classes, Node globals, filesystem, or dynamic code generation.
  globalThis.eval = () => { throw new Error('eval is not available'); };
  const disabledFunction = new Proxy(Function, {
    apply() { throw new Error('Dynamic code execution is not allowed.'); },
    construct() { throw new Error('Dynamic code execution is not allowed.'); },
  });
  Object.defineProperty(Function.prototype, 'constructor', {value: disabledFunction, writable: false, configurable: false});
  globalThis.Function = disabledFunction;
})();
