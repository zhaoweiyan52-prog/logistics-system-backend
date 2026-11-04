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

async function boot({ role='customer', restore=true, expired=false, orderError=false, loginError='', regError='', mock=false, full=false, transport={}, language='zh-CN' }={}) {
  let view;
  const requests=[], messages=[], mounted=[], watchers=[];
  const values = new Map();
  values.set('ls_locale:phase_test',language);
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
    document:{documentElement:{lang:'zh-CN'},title:''},
    console:{log(){}},localStorage:storage,sessionStorage:{getItem:()=>null,setItem(){},removeItem(){}},
    AbortController,URLSearchParams,FormData,setTimeout,clearTimeout,
    Vue:{reactive:x=>x,ref:x=>({value:x}),computed:get=>({get value(){return get();}}),
      watch:(get,callback)=>{callback.get=get;watchers.push(callback);},onMounted:callback=>mounted.push(callback),
      createApp:options=>({use(){return this;},mount(){view=options.setup();mounted.forEach(callback=>callback());}})},
    ElementPlus:{ElMessage:message,ElMessageBox:{confirm:()=>Promise.resolve(),alert:()=>Promise.resolve()}},
    fetch:async (url,options={})=>{
      const method=options.method||'GET'; requests.push({url,method,body:options.body});
      const pathname=url.split('?')[0];
      if(transport.respond) { const response=await transport.respond(url,options); if(response) return response; }
      if (loginError && pathname==='/api/auth/login') return {status:200,ok:true,json:async()=>({code:40101,data:null,message:loginError})};
      if (regError && pathname==='/api/auth/register') return {status:200,ok:true,json:async()=>({code:40901,data:null,message:regError})};
      if (expired && ['/api/orders','/api/settings'].includes(pathname)) return {status:401,ok:false,json:async()=>({})};
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
  for(const file of ['logistics-i18n.js','vendor/element-plus-locale-en.min.js','vendor/element-plus-locale-zh-cn.min.js','vendor/element-plus-locale-zh-tw.min.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname,'../src/main/resources/static',file),'utf8'),context,{filename:file});
  }
  vm.runInContext(script,context,{filename:'logistics-admin.inline.js'});
  await settle(); await settle();
  return {view,requests,messages,watchers,values,document:context.document};
}

for(const role of ['customer','staff','admin']) test(`${role}: language restoration and switching preserve form data and canonical API values`,async()=>{
  const {view,values,document,requests}=await boot({role,full:true,language:'en'});
  assert.equal(view.locale.value,'en');
  assert.equal(document.documentElement.lang,'en');
  assert.equal(view.elementLocale.value.name,'en');
  const original=JSON.stringify(view.s);
  for(const language of ['zh-TW','zh-CN','en']) {
    view.setLocale(language);
    assert.equal(JSON.stringify(view.s),original);
    assert.equal(values.get('ls_locale:phase_test'),language);
    assert.equal(document.documentElement.lang,language);
  }
  if(role==='customer') {
    view.s.sendForm={to:'张三',dest:'测试路1号',goods:'图书文具',weight:1};
    await view.submitSend();
    const body=JSON.parse(requests.find(r=>r.url==='/api/orders' && r.method==='POST').body);
    assert.equal(body.receiver,'张三');assert.equal(body.dest,'测试路1号');assert.equal(body.goods,'图书文具');
  } else if(role==='admin') {
    await view.onRoleChange({id:42,role:'物流员工',name:'张三',account:'another_user'});
    assert.equal(JSON.parse(requests.find(r=>r.url==='/api/users/42/role').body).role,'物流员工');
  } else {
    view.s.invForm={type:'in',goods:'客户原文',qty:1,wh:'华东一号仓'};
    await view.addInv();
    assert.equal(JSON.parse(requests.find(r=>r.url==='/api/inventory'&&r.method==='POST').body).wh,'华东一号仓');
  }
});

test('language preferences are isolated between accounts and survive logout',async()=>{
  const {view,values}=await boot({full:true,language:'en'});
  view.setLocale('zh-TW');
  await view.doLogout(true);
  values.set('ls_token','other-session');
  values.set('ls_user',JSON.stringify({account:'other_user',name:'Other',role:'customer'}));
  view.restoreSession();
  assert.equal(view.locale.value,'zh-CN');
  assert.equal(values.get('ls_locale:phase_test'),'zh-TW');
  view.setLocale('en');
  assert.equal(values.get('ls_locale:other_user'),'en');
  view.setLocale('unsupported');
  assert.equal(view.locale.value,'zh-CN');
});

test('translated messages retain interpolation, server reasons and staff notes',async()=>{
  const {view}=await boot({full:true,language:'en'});
  assert.equal(view.t('共 {count} 单',{count:17}).includes('17'),true);
  assert.doesNotMatch(view.systemText('签收码不正确'),/[\u4e00-\u9fff]/);
  assert.match(view.systemText('已揽收，运输中 · 请打电话'),/ · 请打电话$/);
  assert.equal(view.systemText('Unexpected upstream failure'),'Unexpected upstream failure');
  assert.match(view.systemText('已由张三签收，感谢使用'),/张三/);
  assert.equal(view.systemText('pwd: 密码长度为 6-20 位'),'pwd: '+view.t('密码长度为 6-20 位'));
  assert.equal(view.loginRules.value.account[0].message,view.t('请输入用户名'));
  view.setLocale('zh-TW');
  assert.equal(view.loginRules.value.account[0].message,view.t('请输入用户名'));
});

function onlyPhaseOneRequests(requests) {
  for (const request of requests) {
    const pathname=request.url.split('?')[0];
    assert.ok(['/api/orders','/api/orders/SF_TEST_ORDER','/api/auth/login','/api/auth/register','/api/auth/logout'].includes(pathname),
      `Unexpected request: ${request.method} ${request.url}`);
  }
}

for(const loginError of ['该账号尚未注册，请先注册','密码错误，请重试']) {
  test(`login retains the server reason in the form: ${loginError}`,async()=>{
    const {view}=await boot({restore:false,full:true,loginError});
    view.loginRef.value={validate:async()=>true};
    view.s.auth.login={account:'login_test',pwd:'TestOnly123!'};
    await view.submitLogin();
    assert.equal(view.s.auth.logged,false);
    assert.equal(view.s.auth.submitting,false);
    assert.equal(view.s.auth.loginError,loginError);
    await settle();
    assert.equal(view.s.auth.loginError,loginError);
  });
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

test('changing status and role filters resets the requested page',async()=>{
  assert.match(html,/v-model:page-size="s.orderSize"/);
  for(const [role,filter,page,endpoint] of [['customer','orderFilter','orderPage','/api/orders'],['admin','userRoleFilter','userPage','/api/users']]) {
    const {view,watchers,requests}=await boot({full:true,role});
    view.s[page]=3;
    const previous=watchers.map(w=>JSON.stringify(w.get()));
    view.s[filter]=role==='customer'?'delivered':'物流员工';
    requests.length=0;
    for(let i=0;i<watchers.length;i++) if(JSON.stringify(watchers[i].get())!==previous[i]) await watchers[i]();
    await settle();
    assert.equal(view.s[page],1);
    assert.ok(requests.some(r=>r.url.startsWith(endpoint+'?')&&new URL(r.url,'http://test').searchParams.get('page')==='1'));
  }
});

test('voucher upload shares authentication handling without a JSON content type',async()=>{
  const transport={};
  const {view,requests}=await boot({full:true,role:'staff',transport});
  view.s.voucherTask={id:42};
  const file={name:'proof.png',raw:new Blob(['test'],{type:'image/png'})};
  view.onVoucherFile(file,[file]);
  transport.respond=async(url,options)=>{
    if(url==='/api/tasks/42/voucher') {
      assert.ok(options.body instanceof FormData);
      assert.equal(options.headers['Content-Type'],undefined);
      assert.equal(options.headers.Authorization,'Bearer test-session');
      return {status:401,ok:false,json:async()=>({})};
    }
  };
  await view.saveVoucher();
  assert.equal(requests.filter(r=>r.url==='/api/tasks/42/voucher').length,1);
  assert.equal(view.s.auth.logged,false);
  assert.equal(view.s.auth.loginError,'登录已失效，请重新登录');
});

for(const status of [200,401]) test(`old session response ${status} cannot refill data or log out the new session`,async()=>{
  const transport={};
  const {view,messages}=await boot({full:true,transport});
  let release;
  transport.respond=()=>new Promise(resolve=>{release=resolve;});
  const pending=view.loadOrders();
  await view.doLogout(true);
  transport.respond=null;
  view.loginRef.value={validate:async()=>true};
  view.s.auth.login={account:'next_user',pwd:'TestOnly123!'};
  await view.submitLogin();await settle();
  view.s.orderList=[];
  const count=messages.length;
  release({status,ok:status===200,json:async()=>({code:0,data:{list:[{no:'OLD_PRIVATE_ORDER'}],total:1}})});
  await pending;
  assert.equal(view.s.auth.logged,true);
  assert.equal(view.s.orderList.length,0);
  assert.equal(messages.length,count);
});

test('failed server logout does not claim successful revocation',async()=>{
  const transport={};
  const {view,messages}=await boot({full:true,transport});
  transport.respond=async url=>{if(url==='/api/auth/logout') throw new Error('Network unavailable');};
  await view.doLogout(false);await settle();
  assert.equal(view.s.auth.logged,true);
  assert.ok(messages.some(m=>m.level==='error'&&m.text.includes('Network unavailable')));
  assert.ok(!messages.some(m=>m.level==='success'&&m.text.includes('已安全退出')));
});

test('successful logout waits for server revocation before clearing the session',async()=>{
  const transport={};
  const {view,messages}=await boot({full:true,transport});
  let release;
  transport.respond=()=>new Promise(resolve=>{release=resolve;});
  const pending=view.doLogout(false);
  assert.equal(view.s.auth.logged,true);
  release({status:200,ok:true,json:async()=>({code:0,data:{ok:true}})});
  await pending;
  assert.equal(view.s.auth.logged,false);
  assert.equal(view.CONFIG.TOKEN,'');
  assert.ok(messages.some(m=>m.level==='success'&&m.text==='已安全退出登录'));
});

test('session guard also checks after a delayed JSON body finishes reading',async()=>{
  const transport={};
  const {view}=await boot({full:true,transport});
  let release;
  transport.respond=async()=>({status:200,ok:true,json:()=>new Promise(resolve=>{release=resolve;})});
  const pending=view.loadOrders();await settle();
  await view.doLogout(true);
  release({code:0,data:{list:[{no:'OLD_PRIVATE_ORDER'}],total:1}});
  await pending;
  assert.equal(view.s.orderList.length,0);
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

test('expired restored token clears session and retains the reason inside the login form',async()=>{
  for(const full of [false,true]) {
    const {view,requests}=await boot({expired:true,full});
    assert.equal(view.s.auth.logged,false);
    assert.equal(requests.length,1);
    assert.equal(view.s.auth.loginError,'登录已失效，请重新登录');
    await settle();
    assert.equal(view.s.auth.loginError,'登录已失效，请重新登录');
  }
});

test('registration failure stays inside the form and retry can succeed',async()=>{
  const {view}=await boot({restore:false,full:true,regError:'该用户名已被注册，请直接登录'});
  view.regRef.value={validate:async()=>true};
  view.s.auth.reg={account:'repair_demo',pwd:'TestOnly123!',role:'customer'};
  await view.submitReg();
  assert.equal(view.s.auth.logged,false);
  assert.equal(view.s.auth.submitting,false);
  assert.equal(view.s.auth.regError,'该用户名已被注册，请直接登录');
  assert.match(html, /<el-alert v-if="s.auth.regError"[^>]+:title="systemText\(s.auth.regError\)"[^>]+:closable="false"/);
  for(const field of ['account','pwd','pwd2']) assert.ok(html.includes(`v-model="s.auth.reg.${field}" @input="s.auth.regError=''"`));
  const retry=await boot({restore:false,full:true});
  retry.view.regRef.value={validate:async()=>true,clearValidate(){}};
  retry.view.s.auth.regError='旧错误';
  await retry.view.submitReg();
  assert.equal(retry.view.s.auth.logged,true);
  assert.equal(retry.view.s.auth.regError,'');
});

test('forgot password gives visible, accurate guidance for the current data source',async()=>{
  for(const mock of [false,true]) {
    const {view}=await boot({restore:false,full:true,mock});
    view.forgotPwd();
    assert.ok(view.s.auth.loginError);
    assert.equal(view.s.auth.loginError.includes('123456'),mock);
    if(!mock) assert.match(view.s.auth.loginError,/重置流程尚未开放/);
  }
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
