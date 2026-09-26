// Real Chromium, two independent browser contexts, an isolated SQLite test server.
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const {chromium} = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const url = process.env.STUDYDESK_TEST_URL || 'http://127.0.0.1:18765';
const artifacts = path.join(__dirname, 'artifacts');
fs.mkdirSync(artifacts, {recursive:true});

(async()=>{
  const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH,headless:true,args:['--no-sandbox']});
  const mobile=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const desktop=await browser.newContext({viewport:{width:1440,height:1080}});
  const phone=await mobile.newPage(),pc=await desktop.newPage(),errors=[];
  for(const p of [phone,pc]){p.on('pageerror',e=>errors.push(e.message));p.on('dialog',d=>d.accept());}
  const visit=async(p,route)=>{await p.goto(url+'/#/'+route);await p.waitForSelector('h1');await p.waitForFunction(()=>!document.querySelector('.skeleton'));};
  const api=async(p,route,body)=>p.evaluate(async({route,body})=>{
    const r=await fetch(route,{method:body?'POST':'GET',headers:body?{'Content-Type':'application/json','X-StudyDesk':'1'}:{},body:body?JSON.stringify(body):undefined});
    return {status:r.status,data:await r.json()};
  },{route,body});
  try{
    await visit(phone,'home');await visit(pc,'home');
    await phone.screenshot({path:path.join(artifacts,'mobile-home.png'),fullPage:true});
    await pc.screenshot({path:path.join(artifacts,'desktop-home.png'),fullPage:true});
    for(const route of ['write/resnet-01','words','schedule','notebook','settings','chat']){
      await visit(phone,route);
      assert.equal(await phone.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,'No viewport overflow: '+route);
    }
    await visit(phone,'write/resnet-01');
    await phone.locator('#translation').fill('Training become more difficulty.');
    await phone.waitForFunction(()=>document.querySelector('#draft-state')?.textContent==='已同步');
    await visit(pc,'write/resnet-01');
    assert.equal(await pc.locator('#translation').inputValue(),'Training become more difficulty.','Draft synchronizes across devices');
    const stale=await api(pc,'/api/drafts/resnet-01',{text:'stale overwrite',revision:0});
    assert.equal(stale.status,409,'Stale writes rejected');
    await phone.locator('[data-act="submit-review"]').click();
    await phone.waitForSelector('.feedback-card',{timeout:12000});
    assert.equal(await phone.locator('.correction .before').first().textContent(),'more difficulty');
    await phone.screenshot({path:path.join(artifacts,'mobile-writing.png'),fullPage:true});
    await pc.waitForSelector('.feedback-card',{timeout:12000});
    await visit(phone,'notebook');await phone.waitForSelector('.note-card');
    assert.equal(await phone.locator('.note-card').count(),3,'Feedback creates mistake, vocabulary, pattern');
    await phone.locator('[data-act="note-review"]').first().click();
    assert.equal(await phone.locator('#note-answer .reference').count(),0,'Note recall first');
    await phone.locator('[data-act="reveal-note"]').click();
    await phone.locator('[data-act="note-rate"][data-id="known"]').click();
    await phone.waitForFunction(()=>!document.querySelector('#modal').open);
    await visit(phone,'words');
    const word=await phone.locator('.word-heading h2').textContent();
    assert.equal(await phone.locator('.definition').count(),0,'Definition hidden before recall');
    await phone.locator('[data-act="rate-word"][data-id="known"]').click();
    await phone.waitForSelector('.definition');
    assert.equal(await phone.locator('.word-heading h2').textContent(),word,'Rating stays on same card');
    const dashboard=(await api(pc,'/api/dashboard')).data;
    assert.equal(dashboard.counts.vocabulary,1,'Vocabulary progress shared');
    await phone.locator('[data-act="next-word"]').click();
    await phone.waitForFunction(w=>document.querySelector('.word-heading h2')?.textContent!==w,word);
    await visit(phone,'schedule');
    await phone.locator('#schedule-week').selectOption('7');
    await phone.waitForFunction(()=>document.querySelector('#schedule-week')?.value==='7'&&document.querySelectorAll('.course-block').length>0);
    const schedule=(await api(pc,'/api/schedule?week=7')).data;
    assert(schedule.items.some(o=>o.date==='2026-11-01'&&o.course.name==='数据库系统原理与应用'),'Sunday class matches supplied table');
    await phone.locator('[data-act="add-course"]').click();
    await phone.locator('#course-form [name=name]').fill('同步测试课程');
    await phone.locator('#course-form [name=weekday]').selectOption('7');
    await phone.locator('#course-form [name=sections]').fill('9-10');
    await phone.locator('#course-form [name=weeks]').fill('7');
    await phone.locator('#course-form [name=room]').fill('测试教室');
    await phone.locator('#course-form button[type=submit]').click();
    await phone.waitForFunction(()=>!document.querySelector('#modal').open);
    const after=(await api(pc,'/api/schedule?week=7')).data;
    assert(after.items.some(o=>o.course.name==='同步测试课程'),'Course visible to second device');
    await visit(phone,'chat');
    await phone.locator('#chat-question').fill('difficult 和 difficulty 有什么不同？');
    await phone.locator('#chat-form button').click();
    await phone.waitForSelector('.chat-message:not(.user)',{timeout:12000});
    await phone.waitForFunction(()=>document.querySelector('.chat-messages')?.textContent.includes('请用形容词'));
    const backup=(await api(pc,'/api/backup')).data;
    assert.equal(backup.tables.attempts.length,1);
    assert.equal(backup.tables.notebook.length,3);
    assert(!JSON.stringify(backup).includes('GEMINI_API_KEY'),'No credentials in user backup');
    assert.equal((await api(pc,'/.env')).status,404);
    await visit(pc,'write/resnet-01');
    await pc.screenshot({path:path.join(artifacts,'desktop-writing.png'),fullPage:true});
    assert.deepEqual(errors,[],'No JavaScript exceptions');
    console.log('PASS: mobile + desktop, no overflow, draft sync/conflict, review, notebook recall, vocabulary recall/sync, course CRUD, chat, backup, secret isolation');
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
