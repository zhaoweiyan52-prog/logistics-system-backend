// Exercises the actual page script with a small Vue lifecycle and HTTP test harness.
// Run: node scripts/test-phase-one-frontend.cjs
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const html = fs.readFileSync(path.join(__dirname, '../src/main/resources/static/logistics-admin.html'), 'utf8');
const source = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');
const settle = () => new Promise(resolve => setImmediate(resolve));
test('in-DOM Vue components use explicit end tags so table columns do not become nested',()=>{
  assert.doesNotMatch(html, /<el-[\w-]+\b(?:"[^"]*"|'[^']*'|[^'">])*\/>/);
});

async function boot({ role='customer', restore=true, expired=false, orderError=false, mock=false, full=false }={}) {
  let view;
  const requests=[], messages=[], mounted=[], watchers=[];
  const values = new Map();
  if (restore) {
    values.set('ls_token', 'test-session');
    values.set('ls_user', JSON.stringify({account:'phase_test',name:'Test',role}));
  }
  const storage = {getItem:k=>values.get(k)||null,setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)};
  const message = {};
  for (const level of ['success','error','info','warning']) message[level] = text => messages.push({level,text});
  const sample = {no:'SF_TEST_ORDER',receiver:'Test',dest:'Test address',goods:'Books',weight:1,fee:12,
    status:'pending',time:'2026-10-26T00:00:00Z',eta:'2026-10-29T00:00:00Z',traces:[],fb:null};
  const context = vm.createContext({
    console:{log(){}},localStorage:storage,sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},
    AbortController,URLSearchParams,FormData,setTimeout,clearTimeout,
    Vue:{reactive:x=>x,ref:x=>({value:x}),computed:get=>({get value(){return get();}}),
      watch:(get,callback)=>watchers.push(callback),onMounted:callback=>mounted.push(callback),
      createApp:options=>({use(){return this;},mount(){view=options.setup();mounted.forEach(callback=>callback());}})},
    ElementPlus:{ElMessage:message,ElMessageBox:{confirm:()=>Promise.resolve(),alert:()=>Promise.resolve()}},
    fetch:async (url,options={})=>{
      const method=options.method||'GET'; requests.push({url,method,body:options.body});
      const pathname=url.split('?')[0];
      if (expired && pathname==='/api/orders') return {status:401,ok:false,json:async()=>({})};
      if (orderError && pathname==='/api/orders') return {status:200,ok:true,json:async()=>({code:50001,message:'Database unavailable'})};
      let data;
      if (pathname==='/api/auth/login' || pathname==='/api/auth/register') data={token:'new-test-session',role,name:'Test',account:'phase_test'};
      else if (pathname==='/api/auth/logout') data={ok:true};
      else if (pathname==='/api/orders') data=method==='POST'?sample:{list:[sample],total:1};
      else if (pathname==='/api/orders/SF_TEST_ORDER') data=sample;
      else if (pathname==='/api/stats/overview') data={total:1};
      else if (pathname==='/api/settings') data={name:'测试物流',phone:'400-800-1234',notice:'',first:1,firstFee:12,addFee:4,signNtf:true,excNtf:true,mktNtf:false};
      else if (/\/api\/users\/\d+\/(active|role)$/.test(pathname)) data={ok:true};
      else if (pathname==='/api/tasks' || pathname==='/api/users') data={list:[],total:0};
      else data=[];
      return {status:200,ok:true,json:async()=>({code:0,data,message:'OK'})};
    }
  });
  let script=source;
  if (mock) script=script.replace('USE_MOCK: false,','USE_MOCK: true,');
  if (!full) script=script.replace('PHASE_ONE: false,','PHASE_ONE: true,');
  vm.runInContext(script,context,{filename:'logistics-admin.inline.js'});
  await settle(); await settle();
  return {view,requests,messages,watchers};
}

function onlyPhaseOneRequests(requests) {
  for (const request of requests) {
    const pathname=request.url.split('?')[0];
    assert.ok(['/api/orders','/api/orders/SF_TEST_ORDER','/api/auth/login','/api/auth/register','/api/auth/logout'].includes(pathname),
      `Unexpected request: ${request.method} ${request.url}`);
  }
}

for (const role of ['customer','staff','admin']) {
  test(`${role}: restoring session, refreshing and watchers do not request unfinished modules`, async()=>{
    const {view,requests,messages,watchers}=await boot({role});
    assert.equal(view.s.auth.logged,true);
    assert.equal(view.s.active,role==='customer'?'home':'phaseIntro');
    await view.loadAll(); await view.loadTasks(); await view.loadUsers();
    // Pagination/filter watchers are also guarded, even if stale UI state changes.
    await watchers[2](); await watchers[4](); await settle();
    onlyPhaseOneRequests(requests);
    assert.equal(messages.filter(m=>m.level==='error').length,0);
    if (role!=='customer') assert.deepEqual(Array.from(view.menus.value,m=>m.k),['phaseIntro']);
  });
}

test('guest: initialization and loaders make no protected requests',async()=>{
  const {view,requests}=await boot({restore:false});
  await view.loadAll(); await view.loadOrders(); await view.loadTasks(); await view.loadUsers();
  assert.equal(requests.length,0);
});

test('login and registration initialize only implemented features',async()=>{
  for (const role of ['customer','staff']) {
    for (const action of ['login','register']) {
      const {view,requests,messages}=await boot({role,restore:false});
      view.loginRef.value={validate:async()=>true};
      view.regRef.value={validate:async()=>true,clearValidate(){}};
      view.s.auth.login={account:'phase_test',pwd:'TestPassword123'};
      view.s.auth.reg={account:'phase_test',pwd:'TestPassword123',role};
      await (action==='login'?view.submitLogin():view.submitReg()); await settle();
      assert.equal(view.s.auth.logged,true);
      onlyPhaseOneRequests(requests);
      assert.equal(messages.filter(m=>m.level==='error').length,0);
    }
  }
});

test('creating, filtering and opening an order never refresh unsupported statistics',async()=>{
  const {view,requests,messages}=await boot();
  view.s.sendForm={to:'Test',dest:'Test address',goods:'Books',weight:1};
  await view.submitSend(); await settle();
  assert.ok(requests.some(r=>r.method==='POST'&&r.url==='/api/orders'));
  view.resetOrderFilter();
  view.trackOrder(view.s.orderList[0]); await settle();
  assert.equal(view.s.trackOrder.no,'SF_TEST_ORDER');
  onlyPhaseOneRequests(requests);
  assert.equal(messages.filter(m=>m.level==='error').length,0);
});

test('unfinished actions and role switching are blocked before network requests',async()=>{
  const {view,requests}=await boot();
  requests.length=0;
  view.switchRole('admin'); view.go('settings'); view.openFeedback({no:'SF_TEST_ORDER'});
  await view.saveFeedback(); await view.confirmSign(); await view.saveStatus();
  await view.saveVoucher(); await view.addInv(); await view.onRoleChange({}); await view.batchAssign();
  assert.equal(view.s.role,'customer');
  assert.equal(view.s.active,'home');
  assert.equal(requests.length,0);
});

test('real implemented-endpoint errors remain visible',async()=>{
  const {messages}=await boot({orderError:true});
  assert.ok(messages.some(m=>m.level==='error'&&m.text.includes('Database unavailable')));
});

test('expired restored token clears session without recursive logout requests',async()=>{
  const {view,requests,messages}=await boot({expired:true});
  assert.equal(view.s.auth.logged,false);
  assert.equal(requests.length,1);
  assert.ok(messages.some(m=>m.level==='error'&&m.text.includes('登录已失效')));
});

test('mock mode retains the full demo and does not call HTTP',async()=>{
  const {view,requests}=await boot({mock:true,role:'staff'});
  assert.equal(view.phaseOne,false);
  assert.equal(view.s.active,'work');
  assert.ok(view.s.taskList.length>0);
  assert.equal(requests.length,0);
});

for(const role of ['customer','staff','admin']) {
  test(`full backend ${role}: loads allowed modules and cannot switch identity`,async()=>{
    const {view,requests,messages}=await boot({full:true,role});
    assert.equal(view.phaseOne,false);
    assert.equal(view.s.active,{customer:'home',staff:'work',admin:'dash'}[role]);
    const allowed=['/api/settings','/api/stats/overview','/api/stats/weekly','/api/notifications',
      ...({customer:['/api/orders'],staff:['/api/tasks','/api/inventory','/api/warehouses'],admin:['/api/orders','/api/users','/api/stats/monthly']})[role]];
    for(const r of requests) assert.ok(allowed.includes(r.url.split('?')[0]),`Unexpected ${role} request ${r.url}`);
    for(const path of allowed) assert.ok(requests.some(r=>r.url.split('?')[0]===path),`Missing ${path}`);
    view.switchRole(role==='admin'?'customer':'admin');
    assert.equal(view.s.role,role);
    assert.equal(messages.filter(m=>m.level==='error').length,0);
  });
}
test('admin settings and enable/disable buttons send real persistence requests',async()=>{
  const {view,requests}=await boot({full:true,role:'admin'});
  view.s.setForm.name='新物流名称';await view.saveSettings();
  await view.toggleUserActive({id:42,account:'another_user',active:true});
  assert.ok(requests.some(r=>r.url==='/api/settings'&&r.method==='PUT'&&JSON.parse(r.body).name==='新物流名称'));
  assert.ok(requests.some(r=>r.url==='/api/users/42/active'&&r.method==='PATCH'&&JSON.parse(r.body).active===false));
});
