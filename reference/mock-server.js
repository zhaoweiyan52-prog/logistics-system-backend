/* ==========================================================
 * 物流管理系统 · Mock 后端（零依赖，仅用 Node 内建模块）
 *   启动： node mock-server.js
 *   访问： http://localhost:8080          （静态托管页面，避开 file:// 限制）
 *         http://localhost:8080/api/...   （接口）
 *  说明：本文件是接口契约的「参考实现」，数据用固定种子生成，保证与前端 mock 演示数据一致。
 * ========================================================== */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 8080;
const ROOT = __dirname;

/* ---------- 工具 ---------- */
function mulberry32(a) {
  return function () {
    a |= 0; a = a + 0x6D2B79F5 | 0;
    let t = Math.imul(a ^ a >>> 15, 1 | a);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}
const rnd = mulberry32(20261025);
const pick = arr => arr[Math.floor(rnd() * arr.length)];
const ri = (min, max) => Math.floor(min + rnd() * (max - min + 1));
const pad2 = n => String(n).padStart(2, '0');
function fmtFull(t) {
  const d = new Date(t);
  return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate()) + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes());
}

/* ---------- 字典 ---------- */
const goodsList = ['日用百货','电子配件','服装鞋帽','食品生鲜','图书文具','母婴用品','美妆护肤','数码产品','家居用品','运动器材'];
const cityList = ['上海市浦东新区','北京市朝阳区','广州市天河区','深圳市南山区','杭州市西湖区','成都市锦江区','武汉市洪山区','南京市鼓楼区','重庆市渝中区','西安市雁塔区','长沙市岳麓区','青岛市市南区'];
const nameList = ['王伟','李娜','张敏','刘强','陈静','杨洋','赵磊','黄丽','周杰','吴霞','徐明','孙婷','朱婷','胡军','郭欣','何平','高雅','林峰'];
const whList = ['华东一号仓','华东二号仓','华南仓','华北仓','西南仓','华中仓'];
const stTextMap = { pending:'待揽收', transit:'运输中', delivering:'派送中', delivered:'已签收', exception:'异常', cancelled:'已取消' };
const ROLE_LABEL = { customer:'客户', staff:'物流员工', admin:'系统管理员' };

const NOW = new Date(2026, 9, 25, 14, 30).getTime();
const DAY = 86400000;

/* ---------- 生成半年运单 ---------- */
let seq = 1000;
function genOrder() {
  const daysAgo = Math.floor(rnd() * rnd() * 180);
  const time = NOW - daysAgo * DAY - ri(0, 20) * 3600000;
  const d = new Date(time);
  const no = 'SF' + d.getFullYear() + pad2(d.getMonth() + 1) + pad2(d.getDate()) + (++seq);
  const r = rnd();
  let status;
  if (daysAgo <= 2) status = r < 0.25 ? 'pending' : r < 0.55 ? 'transit' : r < 0.85 ? 'delivering' : 'delivered';
  else if (daysAgo <= 7) status = r < 0.1 ? 'pending' : r < 0.3 ? 'transit' : r < 0.45 ? 'delivering' : r < 0.92 ? 'delivered' : 'exception';
  else status = r < 0.8 ? 'delivered' : r < 0.88 ? 'exception' : r < 0.95 ? 'cancelled' : 'delivered';
  const fee = +(ri(12, 120) + rnd()).toFixed(2);
  const o = { no, goods: pick(goodsList), dest: pick(cityList), receiver: pick(nameList),
    fee, status, time, eta: time + ri(2, 5) * DAY, signCode: String(ri(1000, 9999)), traces: [] };
  const tr = [{ t: o.time, txt: '寄件成功，等待揽收', done: true }];
  if (status !== 'pending' && status !== 'cancelled') tr.push({ t: o.time + 8 * 3600000, txt: '快件已从' + pick(cityList) + '发出', done: true });
  if (['transit','delivering','delivered','exception'].includes(status)) tr.push({ t: o.time + DAY, txt: '到达转运中心，正在分拣', done: true });
  if (['delivering','delivered','exception'].includes(status)) tr.push({ t: o.time + 2 * DAY, txt: '快递员已取件派送中', done: true });
  if (status === 'delivering') tr.push({ t: o.eta, txt: '正在派送，签收码 ' + o.signCode, done: false });
  if (status === 'delivered') tr.push({ t: o.time + 2.5 * DAY, txt: '已由本人签收，感谢使用', done: true });
  if (status === 'exception') tr.push({ t: o.time + 2 * DAY, txt: '外包装破损，已启动理赔流程', done: false });
  if (status === 'cancelled') tr.push({ t: o.time + 3600000, txt: '订单已取消，费用原路退回', done: false });
  o.traces = tr;
  return o;
}
const orders = Array.from({ length: 522 }, genOrder).sort((a, b) => b.time - a.time);

/* 历史反馈 */
const fbMap = {};
orders.filter(o => o.status === 'delivered' && rnd() < 0.5).slice(0, 133).forEach(o => {
  const type = pick(['服务态度','配送时效','包装完好','其他']);
  fbMap[o.no] = { type,
    content: type === '配送时效' ? '送达时间比预期晚，希望提升时效' : type === '服务态度' ? '快递员服务态度很好，点赞' : type === '包装完好' ? '包装严实，物品完好无损' : '整体体验不错，继续保持',
    time: fmtFull(o.time + 3 * DAY), reply: rnd() < 0.6 ? '感谢您的反馈，我们已记录并持续改进服务。' : '' };
});
orders.forEach(o => { o.fb = fbMap[o.no] || null; });

/* 员工待办 */
const tasks = [];
const activeOrders = orders.filter(o => ['pending','delivering','transit'].includes(o.status));
for (let i = 0; i < 87; i++) {
  const o = activeOrders[i % activeOrders.length];
  tasks.push({ id: i + 1, no: o.no, type: rnd() < 0.5 ? '上门揽收' : '派送',
    addr: o.dest + pick(['某小区','某大厦','某产业园','某街道']),
    time: NOW - ri(0, 6) * DAY + ri(0, 10) * 3600000, status: i < 15 ? 'todo' : 'done', voucher: rnd() < 0.7 });
}

/* 用户 */
const users = Array.from({ length: 42 }, (_, i) => ({
  id: i + 1, name: pick(nameList), account: 'user' + String(1000 + i),
  role: i < 32 ? '客户' : i < 40 ? '物流员工' : '系统管理员',
  active: rnd() > 0.08, orderCount: ri(0, 140), created: NOW - ri(30, 540) * DAY
}));

/* 出入库 */
const inv = [];
for (let i = 0; i < 26; i++) inv.push({ no: 'IN' + String(26000 + i), goods: pick(goodsList), qty: ri(5, 80), wh: pick(whList), op: pick(nameList), time: NOW - ri(0, 30) * DAY, type: 'in' });
for (let i = 0; i < 24; i++) inv.push({ no: 'OUT' + String(18000 + i), goods: pick(goodsList), qty: ri(3, 60), wh: pick(whList), op: pick(nameList), time: NOW - ri(0, 30) * DAY, type: 'out' });
inv.sort((a, b) => b.time - a.time);

const warehouses = [
  { name: '华东一号仓', used: 1240, cap: 1500 }, { name: '华东二号仓', used: 890, cap: 1200 },
  { name: '华南仓', used: 1320, cap: 1400 }, { name: '华北仓', used: 620, cap: 1000 },
  { name: '西南仓', used: 445, cap: 800 }, { name: '华中仓', used: 760, cap: 900 }
];

const notices = [
  { id:1, icon:'📦', title:'包裹已签收', desc:'今日有 1 个包裹由本人签收', time:'10 分钟前', read:false, act:'track' },
  { id:2, icon:'✍️', title:'待您签收', desc:'有新包裹正在派送，请留意签收码', time:'1 小时前', read:false, act:'track' },
  { id:3, icon:'💬', title:'反馈已回复', desc:'您关于配送时效的反馈客服已回复', time:'今天 09:12', read:false, act:'orders' },
  { id:4, icon:'⚠️', title:'异常运单提醒', desc:'有运单外箱破损，已启动理赔', time:'昨天 18:40', read:true, act:'track' },
  { id:5, icon:'🎉', title:'会员权益升级', desc:'半年累计寄件已达金牌标准', time:'3 天前', read:true, act:'home' }
];

/* ================= 认证 ================= */
/* 账号体系简化为「用户名 + 密码」，不再保留手机号与短信验证码 */
const accounts = [
  { account:'zhangsan', pwd:'123456', name:'张三', role:'customer' },
  { account:'staff01',  pwd:'123456', name:'李师傅', role:'staff' },
  { account:'admin',    pwd:'123456', name:'王主任', role:'admin' }
];
const USERNAME_RE = /^[A-Za-z0-9_]{4,16}$/;
const tokens = new Map();          // token -> { account, role, name, loginAt }
function issueToken(a) {
  const tk = 'mock.' + a.role + '.' + Date.now() + '.' + Math.random().toString(36).slice(2, 8);
  tokens.set(tk, { account: a.account, role: a.role, name: a.name, loginAt: fmtFull(Date.now()) });
  return tk;
}
function authOf(reqHeaders) {
  const h = reqHeaders['authorization'] || '';
  const tk = h.replace(/^Bearer\s+/i, '').trim();
  return tokens.has(tk) ? Object.assign({ token: tk }, tokens.get(tk)) : null;
}

/* ================= 响应封装 ================= */
function send(res, data, status) {
  res.writeHead(status || 200, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,PUT,DELETE,OPTIONS'
  });
  res.end(JSON.stringify({ code: 0, data: data }, null, 0));
}
function fail(res, message, status, code) {
  res.writeHead(status || 400, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET,POST,PATCH,PUT,DELETE,OPTIONS'
  });
  res.end(JSON.stringify({ code: code || 400, message: message }));
}

/* ================= 路由 ================= */
function readBody(req) {
  return new Promise(resolve => {
    let b = '';
    req.on('data', c => { b += c; });
    req.on('end', () => {
      try { resolve(b ? JSON.parse(b) : {}); } catch (e) { resolve({}); }
    });
  });
}
function paged(list, page, size) {
  page = Math.max(1, parseInt(page || 1, 10));
  size = Math.max(1, parseInt(size || 10, 10));
  return { list: list.slice((page - 1) * size, page * size), total: list.length };
}

async function handleApi(req, res, pathname, query) {
  const body = (req.method === 'POST' || req.method === 'PATCH') ? await readBody(req) : {};
  const m = pathname.match(/^\/api\/(.+)$/);
  const p = m ? m[1] : '';
  const auth = authOf(req.headers);
  const needAuth = !(p === 'auth/login' || p === 'auth/register');
  if (needAuth && !auth) return fail(res, '登录已失效，请重新登录', 401, 401);

  /* ---- 认证 ---- */
  if (p === 'auth/login' && req.method === 'POST') {
    const kw = String(body.username || body.account || '').trim();
    const a = accounts.find(x => x.account === kw);
    if (!a) return fail(res, '用户不存在，请先注册');
    if (a.pwd !== body.password) return fail(res, '密码错误，请重试');
    return send(res, { token: issueToken(a), role: a.role, name: a.name, account: a.account });
  }
  if (p === 'auth/register' && req.method === 'POST') {
    const acc = String(body.username || '').trim();
    if (!USERNAME_RE.test(acc)) return fail(res, '用户名需为 4-16 位字母、数字或下划线');
    if (accounts.some(x => x.account === acc)) return fail(res, '该用户名已被注册，请直接登录');
    if (String(body.password || '').length < 6) return fail(res, '密码至少 6 位');
    const role = body.role === 'staff' ? 'staff' : 'customer';
    const a = { account: acc, pwd: String(body.password),
      name: /^[A-Za-z]/.test(acc) ? acc : '用户' + acc.slice(-4), role: role };
    accounts.push(a);
    users.unshift({ id: 9000 + accounts.length, name: a.name, account: a.account,
      role: ROLE_LABEL[a.role] || '客户', active: true, orderCount: 0, created: Date.now() });
    return send(res, { token: issueToken(a), role: a.role, name: a.name, account: a.account });
  }
  if (p === 'auth/logout' && req.method === 'POST') {
    if (auth) tokens.delete(auth.token);
    return send(res, { ok: true });
  }

  /* ---- 运单 ---- */
  if (p === 'orders' && req.method === 'GET') {
    const kw = query.kw || '';
    const list = orders.filter(o => (!kw || o.no.includes(kw) || o.goods.includes(kw)) && (!query.status || o.status === query.status));
    return send(res, paged(list, query.page, query.size));
  }
  if (p === 'orders' && req.method === 'POST') {
    const d = new Date(NOW);
    const no = 'SF' + d.getFullYear() + pad2(d.getMonth() + 1) + pad2(d.getDate()) + (++seq);
    const o = { no, goods: body.goods || pick(goodsList), dest: body.dest || pick(cityList),
      receiver: body.receiver || '未填写', fee: Number(body.fee) || 20, status: 'pending',
      time: NOW, eta: NOW + 3 * DAY, signCode: String(ri(1000, 9999)), weight: Number(body.weight) || 1,
      traces: [{ t: NOW, txt: '寄件成功，等待揽收', done: true }], fb: null };
    orders.unshift(o);
    return send(res, o);
  }
  const mSign = p.match(/^orders\/([^/]+)\/sign$/);
  if (mSign && req.method === 'POST') {
    const o = orders.find(x => x.no === mSign[1]);
    if (!o) return fail(res, '运单不存在');
    if (o.status !== 'delivering') return fail(res, '该运单当前不可签收');
    if (String(body.code) !== o.signCode) return fail(res, '签收码不正确');
    o.status = 'delivered';
    o.traces.push({ t: NOW, txt: '已由' + (body.by || '本人') + '签收，感谢使用', done: true });
    return send(res, o);
  }
  const mFb = p.match(/^orders\/([^/]+)\/feedback$/);
  if (mFb && req.method === 'POST') {
    const o = orders.find(x => x.no === mFb[1]);
    if (!o) return fail(res, '运单不存在');
    if (fbMap[mFb[1]]) return fail(res, '该运单已提交过反馈');
    if (!body.content || !String(body.content).trim()) return fail(res, '请填写反馈内容');
    const fb = { type: body.type || '其他', content: String(body.content), time: fmtFull(NOW), reply: '' };
    fbMap[mFb[1]] = fb; o.fb = fb;
    return send(res, fb);
  }
  const mOrder = p.match(/^orders\/([^/]+)$/);
  if (mOrder && req.method === 'GET') {
    const o = orders.find(x => x.no === mOrder[1]);
    if (!o) return fail(res, '运单不存在', 404, 404);
    return send(res, Object.assign({}, o, { fb: fbMap[o.no] || null }));
  }

  /* ---- 统计 ---- */
  if (p === 'stats/overview') {
    const by = st => orders.filter(x => x.status === st).length;
    return send(res, {
      total: orders.length, delivered: by('delivered'), exception: by('exception'),
      inTransit: orders.filter(x => x.status === 'transit' || x.status === 'delivering').length,
      pending: by('pending'), cancelled: by('cancelled'),
      revenue: orders.filter(x => x.status !== 'cancelled').reduce((a, b) => a + b.fee, 0),
      fbCount: Object.keys(fbMap).length,
      tasksTodo: tasks.filter(x => x.status === 'todo').length,
      tasksDone: tasks.filter(x => x.status === 'done').length,
      noVoucher: tasks.filter(x => x.status === 'done' && !x.voucher).length,
      monthDeliver: orders.filter(x => ['delivering','delivered'].includes(x.status) && NOW - x.time < 30 * DAY).length
    });
  }
  if (p === 'stats/weekly') {
    const arr = [];
    for (let i = 6; i >= 0; i--) {
      const t0 = NOW - i * DAY, k = new Date(t0); k.setHours(0, 0, 0, 0);
      arr.push({ day: i === 0 ? '今天' : '周' + '日一二三四五六'[k.getDay()],
        n: orders.filter(o => o.time >= k.getTime() && o.time < k.getTime() + DAY).length });
    }
    return send(res, arr);
  }
  if (p === 'stats/monthly') {
    const map = {};
    orders.forEach(o => {
      const d = new Date(o.time), mk = d.getFullYear() + '-' + pad2(d.getMonth() + 1);
      map[mk] = map[mk] || { month: mk, count: 0, delivered: 0, exception: 0, fee: 0 };
      map[mk].count++;
      if (o.status === 'delivered') map[mk].delivered++;
      if (o.status === 'exception') map[mk].exception++;
      if (o.status !== 'cancelled') map[mk].fee += o.fee;
    });
    return send(res, Object.keys(map).sort().map(k => map[k]));
  }

  /* ---- 员工任务 ---- */
  if (p === 'tasks' && req.method === 'GET') {
    const list = tasks.filter(t => !query.status || t.status === query.status).slice().sort((a, b) => b.time - a.time);
    return send(res, paged(list, query.page, query.size));
  }
  const mStatus = p.match(/^tasks\/(\d+)\/status$/);
  if (mStatus && req.method === 'POST') {
    const t = tasks.find(x => x.id === Number(mStatus[1]));
    if (!t) return fail(res, '任务不存在');
    t.status = 'done';
    const o = orders.find(x => x.no === t.no);
    if (o && body.status) {
      o.status = body.status;
      o.traces.push({ t: NOW, txt: (stTextMap[body.status] || body.status) + (body.note ? ' · ' + body.note : ''), done: true });
    }
    return send(res, { ok: true });
  }
  const mVoucher = p.match(/^tasks\/(\d+)\/voucher$/);
  if (mVoucher && req.method === 'POST') {
    const t = tasks.find(x => x.id === Number(mVoucher[1]));
    if (!t) return fail(res, '任务不存在');
    t.voucher = true; t.status = 'done';
    return send(res, { ok: true });
  }

  /* ---- 库存 ---- */
  if (p === 'inventory' && req.method === 'GET') {
    const list = inv.filter(x => !query.type || x.type === query.type);
    if (query.op) {  // 兼容前端未传场合
      // no-op
    }
    return send(res, list);
  }
  if (p === 'inventory' && req.method === 'POST') {
    const rec = { no: (body.type === 'in' ? 'IN' : 'OUT') + (26000 + inv.length),
      goods: body.goods || pick(goodsList), qty: Number(body.qty) || 1, wh: body.wh || whList[0],
      op: auth ? auth.name : '系统', time: NOW, type: body.type === 'out' ? 'out' : 'in' };
    inv.unshift(rec);
    const w = warehouses.find(x => x.name === rec.wh);
    if (w) { w.used += rec.type === 'in' ? rec.qty : -rec.qty; w.used = Math.max(0, w.used); }
    return send(res, rec);
  }
  if (p === 'warehouses') return send(res, warehouses);

  /* ---- 用户与角色 ---- */
  if (p === 'users' && req.method === 'GET') {
    const kw = query.kw || '';
    const list = users.filter(u => (!kw || u.name.includes(kw) || u.account.includes(kw)) && (!query.role || u.role === query.role));
    return send(res, paged(list, query.page, query.size));
  }
  const mRole = p.match(/^users\/(\d+)\/role$/);
  if (mRole && req.method === 'PATCH') {
    const u = users.find(x => x.id === Number(mRole[1]));
    if (!u) return fail(res, '用户不存在');
    u.role = body.role || u.role;
    return send(res, { ok: true });
  }
  if (p === 'users/batch-role' && req.method === 'POST') {
    const ids = body.ids || [];
    users.forEach(u => { if (ids.indexOf(u.id) >= 0) u.role = body.role || u.role; });
    return send(res, { ok: true, count: ids.length });
  }
  if (p === 'notifications') return send(res, notices);

  return fail(res, '接口不存在：' + p, 404, 404);
}

/* ================= 静态文件托管 ================= */
const MIME = { '.html':'text/html; charset=utf-8', '.js':'application/javascript; charset=utf-8',
  '.css':'text/css; charset=utf-8', '.md':'text/markdown; charset=utf-8', '.png':'image/png',
  '.jpg':'image/jpeg', '.ico':'image/x-icon', '.json':'application/json; charset=utf-8' };
function serveStatic(req, res, pathname) {
  let rel = pathname === '/' ? '/logistics-admin.html' : pathname;
  const file = path.join(ROOT, decodeURIComponent(rel));
  if (!file.startsWith(ROOT)) { res.writeHead(403); return res.end('403'); }
  fs.readFile(file, (err, buf) => {
    if (err) { res.writeHead(404, { 'Content-Type':'text/plain; charset=utf-8' }); return res.end('404 Not Found'); }
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream' });
    res.end(buf);
  });
}

/* ================= 启动 ================= */
http.createServer((req, res) => {
  const u = new URL(req.url, 'http://localhost:' + PORT);
  const pathname = u.pathname;
  const query = {};
  u.searchParams.forEach((v, k) => { query[k] = v; });
  console.log(req.method + ' ' + req.url);

  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin':'*',
      'Access-Control-Allow-Headers':'Content-Type, Authorization',
      'Access-Control-Allow-Methods':'GET,POST,PATCH,PUT,DELETE,OPTIONS' });
    return res.end();
  }
  if (pathname.startsWith('/api/')) {
    handleApi(req, res, pathname, query).catch(e => {
      console.error('处理异常：', e);
      if (!res.headersSent) fail(res, '服务端异常：' + e.message, 500, 500);
    });
    return;
  }
  serveStatic(req, res, pathname);
}).listen(PORT, () => {
  console.log('\n==================================================');
  console.log('  物流管理系统 Mock 后端已启动');
  console.log('  页面地址: http://localhost:' + PORT + '/');
  console.log('  接口地址: http://localhost:' + PORT + '/api/...');
  console.log('  演示账号: zhangsan / staff01 / admin （密码 123456）');
  console.log('  注册仅需：用户名 + 密码');
  console.log('==================================================\n');
});
