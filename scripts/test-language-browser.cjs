// Local static files + synthetic API responses. No real accounts or database writes.
// Requires Playwright and Microsoft Edge: node scripts/test-language-browser.cjs
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const http=require('node:http'),vm=require('node:vm'),{chromium}=require('playwright');
const root=path.resolve(__dirname,'../src/main/resources/static');
const out=path.resolve(__dirname,'../target/language-browser');fs.mkdirSync(out,{recursive:true});
const dictionary={};vm.runInNewContext(fs.readFileSync(path.join(root,'logistics-i18n.js'),'utf8'),dictionary);
const {messages}=dictionary.LogisticsI18n;
const placeholders=s=>Array.from(s.matchAll(/\{(\w+)\}/g),m=>m[1]).sort();
for(const locale of ['en','zh-TW']) for(const [key,value] of Object.entries(messages[locale])) {
  assert.deepEqual(placeholders(value),placeholders(key),`${locale}: ${key}`);
  if(locale==='en') assert.doesNotMatch(value,/[\u4e00-\u9fff]/,key);
}
const sample={no:'SF_TEST_ORDER',receiver:'收件人原文',dest:'收件地址原文',goods:'物品原文',weight:1,fee:12,status:'delivering',
  signCode:'1234',time:'2026-11-04T00:00:00Z',eta:'2026-11-05T00:00:00Z',
  traces:[{t:'2026-11-04T00:00:00Z',txt:'快递员正在派送 · 员工备注原文',done:true}],fb:null};
function dataFor(url){
  const name=url.pathname;
  if(name==='/api/settings') return {name:'物流管理系统',phone:'400-800-1234',notice:'公告原文',first:1,firstFee:12,addFee:4,signNtf:true,excNtf:true,mktNtf:false};
  if(name==='/api/orders') return {list:[sample],total:21};
  if(name==='/api/orders/SF_TEST_ORDER') return sample;
  if(name==='/api/stats/overview') return {total:21,delivered:10,inTransit:11,revenue:252};
  if(name==='/api/stats/weekly') return [{day:'今天',n:5},{day:'周一',n:6}];
  if(name==='/api/stats/monthly') return [{month:'2026-11',count:21,delivered:10,exception:0,fee:252}];
  if(name==='/api/notifications') return [{id:1,title:'包裹已签收',desc:'SF_TEST_ORDER 已签收',time:'2026-11-04',read:false,act:'track'}];
  if(name==='/api/tasks') return {list:[{id:1,no:sample.no,type:'派送',status:'todo',orderStatus:'delivering',addr:sample.dest,time:sample.time,voucher:false}],total:1};
  if(name==='/api/users') return {list:[{id:1,name:'姓名原文',account:'account',role:'客户',active:true,orderCount:21,created:sample.time}],total:1};
  if(name==='/api/inventory') return [{no:'IN_TEST',goods:'物品原文',qty:1,wh:'仓库原文',op:'员工原文',time:sample.time,type:'in'}];
  if(name==='/api/warehouses') return [{name:'仓库原文',used:1,cap:100}];
  throw new Error(`Unexpected API ${name}`);
}
const server=http.createServer((req,res)=>{
  const file=path.resolve(root,'.'+decodeURIComponent(req.url.split('?')[0]));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
  res.setHeader('Content-Type',file.endsWith('.html')?'text/html; charset=utf-8':file.endsWith('.js')?'application/javascript; charset=utf-8':'text/css');
  res.end(fs.readFileSync(file));
});
(async()=>{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({channel:'msedge',headless:true});
  const errors=[],results=[];
  try {
    const guestContext=await browser.newContext({viewport:{width:1440,height:1000}});
    const guest=await guestContext.newPage();guest.on('pageerror',error=>errors.push(error.message));
    await guest.route('**/api/**',()=>{throw new Error('Guest language changes must not request the API');});
    await guest.goto(`http://127.0.0.1:${server.address().port}/logistics-admin.html`);
    const guestSelect=guest.locator('.auth-language select');
    await guestSelect.selectOption('en');
    const loginInputs=guest.locator('#pane-login input');
    await loginInputs.nth(0).fill('demo_user');await loginInputs.nth(1).fill('Synthetic123!');
    for(const language of ['zh-TW','zh-CN','en']) {
      await guestSelect.selectOption(language);
      assert.equal(await loginInputs.nth(0).inputValue(),'demo_user');
      assert.equal(await loginInputs.nth(1).inputValue(),'Synthetic123!');
    }
    await guest.getByRole('tab',{name:messages.en['注册账号'],exact:true}).click();
    const regInputs=guest.locator('#pane-reg input');
    await regInputs.nth(0).fill('new_user');await regInputs.nth(1).fill('Synthetic456!');await regInputs.nth(2).fill('Synthetic456!');
    await guestSelect.selectOption('zh-TW');await guestSelect.selectOption('en');
    assert.equal(await regInputs.nth(0).inputValue(),'new_user');
    assert.equal(await regInputs.nth(1).inputValue(),'Synthetic456!');
    assert.equal(await regInputs.nth(2).inputValue(),'Synthetic456!');
    await guest.reload();await guestSelect.waitFor();
    assert.equal(await guestSelect.inputValue(),'en');
    await guest.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    assert.equal(await guest.locator('.auth-panel .el-form-item.is-error').count(),0,'Restoring the language must not validate untouched forms');
    await guest.screenshot({path:path.join(out,'login-en.png'),animations:'disabled'});
    await guest.setViewportSize({width:800,height:900});
    assert.equal(await guestSelect.isVisible(),true);
    await guest.screenshot({path:path.join(out,'login-en-narrow.png'),animations:'disabled'});
    results.push('guest: login/register languages, form preservation and refresh passed');
    await guestContext.close();
    for(const role of ['customer','staff','admin']) {
      const context=await browser.newContext({viewport:{width:1440,height:1000}});
      const page=await context.newPage();page.on('pageerror',error=>errors.push(error.message));
      await page.addInitScript(({role})=>{
        localStorage.setItem('ls_token','synthetic-session');
        localStorage.setItem('ls_user',JSON.stringify({account:'test_'+role,name:'姓名原文',role}));
      },{role});
      await page.route('**/api/**',async route=>{
        assert.equal(route.request().method(),'GET','Switching language must not write to the API');
        await route.fulfill({json:{code:0,data:dataFor(new URL(route.request().url())),message:'OK'}});
      });
      await page.goto(`http://127.0.0.1:${server.address().port}/logistics-admin.html`);
      await page.locator('.language-select').waitFor();
      for(const language of ['zh-CN','zh-TW','en']) {
        await page.locator('.language-select').selectOption(language);
        assert.equal(await page.locator('html').getAttribute('lang'),language);
        const menus=await page.locator('.m-item').count();
        for(let i=0;i<menus;i++) {
          await page.locator('.m-item').nth(i).click();await page.evaluate(()=>Vue.nextTick());
          const text=await page.locator('.main').innerText();
          if(language==='en') assert.doesNotMatch(text.replace(/(公告原文|姓名原文|收件地址原文|物品原文|仓库原文|员工原文|收件人原文|员工备注原文)/g,''),/[\u4e00-\u9fff]/,`${role} menu ${i}: untranslated text`);
          assert.equal(await page.locator('.page-title').isVisible(),true);
        }
        assert.deepEqual(errors,[]);results.push(`${role}/${language}: all menus rendered`);
      }
      if(role==='customer') {
        await page.locator('.m-item').nth(1).click();
        const inputs=page.locator('.main .el-input__inner');
        await inputs.nth(0).fill('收件人原文');await inputs.nth(1).fill('收件地址原文');
        await page.locator('.language-select').selectOption('zh-TW');
        assert.equal(await inputs.nth(0).inputValue(),'收件人原文');assert.equal(await inputs.nth(1).inputValue(),'收件地址原文');
        await page.locator('.language-select').selectOption('en');
        await page.screenshot({path:path.join(out,'customer-en.png'),animations:'disabled'});
        await page.locator('.m-item').nth(2).click();
        await page.locator('.main .el-select').first().click();
        await page.locator('.el-select-dropdown__item:visible').first().click();
        await page.getByText('员工备注原文',{exact:false}).waitFor();
        assert.match(await page.locator('.trace-text').innerText(),/员工备注原文$/);
        await page.screenshot({path:path.join(out,'tracking-en.png'),animations:'disabled'});
        await page.locator('.top-search input').fill('SF_TEST');
        await page.locator('.search-hit').first().click();
        assert.match(await page.locator('.trace-text').innerText(),/员工备注原文$/);
        await page.locator('.top-search input').fill('');
        await page.locator('.m-item').nth(3).click();assert.match(await page.locator('.el-pagination').innerText(),/Total 21/);
        await page.getByRole('button',{name:messages.en['写反馈'],exact:true}).click();
        await page.locator('.el-dialog textarea').fill('反馈原文');
        await page.locator('.el-dialog .el-radio').first().click();
        assert.equal(await page.locator('.el-dialog textarea').inputValue(),'反馈原文');
        assert.doesNotMatch((await page.locator('.el-dialog').innerText()).replace('反馈原文',''),/[\u4e00-\u9fff]/);
        await page.getByRole('button',{name:messages.en['取消'],exact:true}).click();
      } else if(role==='staff') {
        await page.locator('.m-item').nth(0).click();
        assert.equal(await page.locator('.el-table .el-tag').first().innerText(),messages.en['派送']);
        await page.screenshot({path:path.join(out,'staff-en.png'),animations:'disabled'});
        await page.getByRole('button',{name:messages.en['状态更新'],exact:true}).click();
        await page.screenshot({path:path.join(out,'status-en.png'),animations:'disabled'});
        await page.locator('.m-item').nth(1).click();
        await page.getByRole('button',{name:messages.en['+ 新建入库'],exact:true}).click();
        assert.doesNotMatch((await page.locator('.el-dialog').innerText()).replace('华东一号仓',''),/[\u4e00-\u9fff]/);
        await page.getByRole('button',{name:messages.en['取消'],exact:true}).click();
      } else {
        await page.locator('.m-item').nth(1).click();
        assert.equal(await page.locator('.el-table .el-tag').first().innerText(),messages.en['正常']);
        await page.screenshot({path:path.join(out,'admin-en.png'),animations:'disabled'});
        await page.getByRole('button',{name:messages.en['角色权限矩阵'],exact:true}).click();
        assert.doesNotMatch((await page.locator('.main').innerText()).replace(/公告原文|姓名原文/g,''),/[\u4e00-\u9fff]/);
        await page.locator('.m-item').nth(3).click();
        await page.getByRole('tab',{name:messages.en['运费规则'],exact:true}).click();
        await page.getByRole('tab',{name:messages.en['通知设置'],exact:true}).click();
        assert.doesNotMatch((await page.locator('.main').innerText()).replace('公告原文',''),/[\u4e00-\u9fff]/);
      }
      await page.reload();await page.locator('.language-select').waitFor();
      assert.equal(await page.locator('.language-select').inputValue(),'en');
      await context.close();
    }
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({results,errors},null,2));console.log(results.join('\n'));
  } finally {await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
