'use strict';
const $ = (q, root = document) => root.querySelector(q);
const $$ = (q, root = document) => [...root.querySelectorAll(q)];
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const uid = () => window.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
const icons = {
  home:'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z',
  pen:'m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-4-4L5 15zM3 22h18',
  book:'M12 5v16M3 3c4 0 6 0 9 2 3-2 5-2 9-2v15c-4 0-6 0-9 3-3-3-5-3-9-3z',
  calendar:'M8 2v4m8-4v4M3 9h18M4 4h16a1 1 0 0 1 1 1v15a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1M7 13h2m6 0h2m-10 4h2m6 0h2',
  notes:'M6 3h13v18H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2m2 5h7m-7 4h7m-7 4h4M6 3v18',
  settings:'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8m-2-5h4l1 3 3-1 2 3-2 3 2 3-2 3-3-1-1 3h-4l-1-3-3 1-2-3 2-3-2-3 2-3 3 1z',
  arrow:'M4 12h16m-6-6 6 6-6 6',
  left:'m14 6-6 6 6 6',right:'m10 6 6 6-6 6',close:'m6 6 12 12M6 18 18 6',
  spark:'m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5z',
  fire:'M13 2c1 5-3 6-2 10 2-1 3-3 3-5 5 3 6 7 4 11-3 5-11 4-13-1C3 12 7 7 9 6c-1 5 1 6 1 6-1-5 4-6 3-10',
  check:'m5 12 4 4L19 6',cloud:'M6 18a5 5 0 0 1-1-10 7 7 0 0 1 13-1 5 5 0 0 1 0 11M9 15l3-3 3 3m-3-3v9',
  audio:'M4 9h4l5-4v14l-5-4H4zm12-1a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14',
  star:'m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9z',
  chat:'M4 4h16v13H9l-5 4zM8 8h8m-8 5h5',
  plus:'M12 4v16M4 12h16',search:'M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14m5 12 6 6',
  download:'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',upload:'M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5',
  clock:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18m0 4v5l3 2',
  external:'M14 3h7v7m-11 4L21 3M10 3H4v17h17v-6',
  refresh:'M20 7V3l-4 4m4 0a9 9 0 0 0-16 3m0 7v4l4-4m-4 0a9 9 0 0 0 16-3',
  leaf:'M20 3C9 2 2 8 5 16c7 6 16-2 15-13M3 21 16 8',
  trophy:'M7 3h10v6a5 5 0 0 1-10 0zM7 5H3v3a4 4 0 0 0 4 4m10-7h4v3a4 4 0 0 1-4 4m-5 2v7m-4 0h8',
  eye:'M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12m10-3a3 3 0 1 0 0 6 3 3 0 0 0 0-6',
  translate:'M3 4h12M9 2v2m-4 3s2 7 9 9m-2-12c0 6-4 10-9 12m11 5 4-10 4 10m-7-3h6',
  list:'M8 5h13M8 12h13M8 19h13M3 5h.1M3 12h.1M3 19h.1',
};
const icon = (name, small=false) => `<svg class="i${small?' sm':''}" viewBox="0 0 24 24" aria-hidden="true"><path d="${icons[name] || icons.spark}"/></svg>`;
const btn = (label, action, opts={}) => `<button type="button" class="btn ${opts.cls||''}" data-act="${action}" ${opts.id?`data-id="${esc(opts.id)}"`:''} ${opts.disabled?'disabled':''}>${opts.icon?icon(opts.icon,true):''}${label}</button>`;
const ibtn = (name, label, action, id='', selected=false) => `<button type="button" class="icon-btn${selected?' selected':''}" aria-label="${esc(label)}" title="${esc(label)}" data-act="${action}" data-id="${esc(id)}">${icon(name)}</button>`;
const labels = {schedule:'课表',home:'今日',write:'写作',words:'背词',notebook:'积累',settings:'设置',chat:'AI 教练'};
const navs = [['schedule','calendar','课表'],['home','home','今日'],['write','pen','写作'],['words','book','背词'],['notebook','notes','积累']];
const levels = {starter:'基础句子',core:'进阶表达',paragraph:'短段落'};
const categories = ['研究动机','方法介绍','实验结果','实验分析','消融分析','段落衔接'];
let S = {page:'schedule', routeId:'',dashboard:null,catalog:[],exercise:null,word:null,wordRevealed:false,wordZh:false,wordMode:'study',wordQuery:'',wordFilter:'',wordLevel:'',wordOffset:0,notebook:[],noteFilter:'all',noteQuery:'',week:0,selectedWeekday:null,schedule:null,version:0,online:true,dirty:false,draftSaving:null,draftConflict:false,level:'',category:'',renderId:0,settings:null,profile:null,wordPrompts:null};
const pollers = new Map();
let toastTimer, draftTimer, searchTimer;
function store(k,v){try{localStorage.setItem(k,JSON.stringify(v));}catch{}}
function stored(k){try{return JSON.parse(localStorage.getItem(k));}catch{return null;}}
function removeStored(k){try{localStorage.removeItem(k);}catch{}}
function toast(message){const e=$('#toast');e.textContent=message;e.classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.classList.remove('visible'),4200);}
function syncStatus(ok){S.online=ok;const e=$('#sync-status');if(e){e.classList.toggle('offline',!ok);e.setAttribute('aria-label',ok?'已同步':'连接中断');e.title=ok?'已同步':'连接中断 · 请检查网络';}}
async function api(path, body){
  let response;
  try{response=await fetch(path,{method:body===undefined?'GET':'POST',headers:body===undefined?{}:{'Content-Type':'application/json','X-StudyDesk':'1'},body:body===undefined?undefined:JSON.stringify(body),cache:'no-store'});}
  catch{syncStatus(false);throw new Error('暂时连不上服务器。请确认同一局域网及后台服务；本机译文草稿已保留。');}
  syncStatus(true);
  const result=await response.json();
  if(!response.ok){const e=new Error(result.error||'请求未完成');e.status=response.status;throw e;}
  return result;
}
function shell(){
  $('#app').innerHTML=`<div class="app-shell"><aside class="sidebar"><a class="brand" href="#/schedule"><span class="brand-mark">S</span><span>StudyDesk</span></a><nav class="nav">${navs.map(([id,i])=>`<button data-act="nav" data-id="${id}" data-nav="${id}">${icon(i)}${labels[id]}<span class="nav-dot hidden"></span></button>`).join('')}<button data-act="nav" data-id="chat" data-nav="chat">${icon('spark')}AI 教练</button></nav><div class="sidebar-bottom"><div class="profile"><span class="identity hidden" id="profile-identity"><span class="avatar" id="profile-avatar"></span><strong id="profile-name"></strong></span>${ibtn('settings','设置','nav','settings')}</div></div></aside><main class="main"><header class="topbar"><div class="breadcrumb"><b id="page-crumb">课表</b></div><div class="top-actions"><span class="sync-pill" id="sync-status" role="status" aria-label="正在连接" title="正在连接"></span><span class="avatar hidden" id="header-avatar"></span>${ibtn('settings','设置','nav','settings')}</div></header><div id="view" aria-live="polite"></div></main><nav class="mobile-nav" aria-label="主导航">${navs.map(([id,i,l])=>`<button data-act="nav" data-id="${id}" data-nav="${id}">${icon(i)}<span>${l}</span></button>`).join('')}</nav></div>`;
}
function applyProfileUI(){
  const p=S.profile?.value||{displayName:'',showName:false,minimalMode:true};
  const visible=!!p.showName&&!!p.displayName;
  document.body.classList.toggle('minimal-mode',p.minimalMode!==false);
  $('#profile-identity')?.classList.toggle('hidden',!visible);
  $('#header-avatar')?.classList.toggle('hidden',!visible);
  if(visible){$('#profile-name').textContent=p.displayName;$('#profile-avatar').textContent=p.displayName[0];$('#header-avatar').textContent=p.displayName[0];}
}
const pageHead=(title,sub='',extra='')=>`<div class="page-head"><div><h1>${title}</h1>${sub?`<p>${sub}</p>`:''}</div>${extra}</div>`;
const empty=(title,description='',i='leaf')=>`<div class="empty">${icon(i)}<strong>${title}</strong>${description}</div>`;
const dateLabel=d=>new Date(d+'T12:00:00+08:00').toLocaleDateString('zh-CN',{month:'long',day:'numeric',weekday:'long',timeZone:'Asia/Shanghai'});
const timeLabel=d=>new Date(d).toLocaleString('zh-CN',{month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit',timeZone:'Asia/Shanghai'});
function setView(html){$('#view').innerHTML=html;}
async function go(page,id=''){
  if(S.page==='write' && S.dirty){try{await saveDraft();}catch(e){toast(e.message);}}
  if(location.hash===`#/${page}${id?'/'+encodeURIComponent(id):''}`)await render();
  else location.hash=`#/${page}${id?'/'+encodeURIComponent(id):''}`;
}
async function render(){
  const rid=++S.renderId;
  const parts=location.hash.slice(2).split('/');S.page=labels[parts[0]]?parts[0]:'schedule';document.body.classList.toggle('schedule-page',S.page==='schedule');S.routeId=decodeURIComponent(parts[1]||'');
  $('meta[name="theme-color"]')?.setAttribute('content',S.page==='schedule'?'#e9eaf4':'#f6f7f2');
  window.StudyDeskNative?.setScheduleTheme?.(S.page==='schedule');
  $$('.nav [data-nav],.mobile-nav [data-nav]').forEach(e=>{const active=e.dataset.nav===S.page;e.classList.toggle('active',active);e.setAttribute('aria-current',active?'page':'false');$('.nav-dot',e)?.classList.toggle('hidden',!active);});
  $('#page-crumb').textContent=labels[S.page];
  setView('<div class="skeleton"><span class="spinner" style="display:inline-block"></span><p>正在接续你的学习…</p></div>');
  try{
    const profile=await api('/api/profile');
    if(rid!==S.renderId)return;
    if(!S.profile||profile.revision!==S.profile.revision){S.profile=profile;applyProfileUI();}
    if(S.page==='home'){const d=await api('/api/dashboard');if(rid!==S.renderId)return;S.dashboard=d;S.version=d.version;renderHome(d);}
    if(S.page==='write'){await loadWriting(rid);}
    if(S.page==='words'){await loadWords(rid);}
    if(S.page==='schedule'){const d=await api('/api/schedule'+(S.week?'?week='+S.week:''));if(rid!==S.renderId)return;S.schedule=d;S.week=d.week;renderSchedule();}
    if(S.page==='notebook'){const d=await api('/api/notebook');if(rid!==S.renderId)return;S.notebook=d;renderNotebook();}
    if(S.page==='settings'){const [settings,models,prompts]=await Promise.all([api('/api/settings'),api('/api/models'),api('/api/word-prompts')]);if(rid!==S.renderId)return;S.settings=settings;S.models=models;S.wordPrompts=prompts;renderSettings();}
    if(S.page==='chat'){const h=await api('/api/chat');if(rid!==S.renderId)return;renderChat(h);}
  }catch(e){if(rid===S.renderId)setView(pageHead('暂时没有连上')+`<div class="error-banner">${esc(e.message)}</div>${btn('重新连接','reload',{icon:'refresh'})}`);}
}
function renderHome(d){
  const words=d.counts.vocabulary||0,writing=d.counts.writing||0;
  const name=S.profile?.value?.showName?S.profile.value.displayName:'';
  const stats=[['今日写作',writing+' 次'],['今日背词',words+' 词'],['待复习',d.dueWords+' 词'],['表达积累',d.noteCount+' 条']];
  setView(pageHead(name?`${esc(name)} · 今日`:'今日学习',`第 ${d.week} 周 · ${dateLabel(d.today)}`)+`
    <div class="stats-grid">${stats.map(([label,value])=>`<div class="stat-card"><span>${label}</span><strong>${value}</strong></div>`).join('')}</div>
    <div class="quick-actions">${btn('写作练习','start-writing',{icon:'pen'})}${btn('开始背词','start-words',{cls:'secondary',icon:'book'})}${btn('复习积累','review-notes',{cls:'secondary',icon:'notes'})}</div>
    <div class="home-grid"><section class="panel"><div class="section-title"><h2>今日课程</h2>${btn('整周课表','nav',{id:'schedule',cls:'ghost small',icon:'right'})}</div>${d.todayCourses.length?d.todayCourses.map(o=>`<div class="course-row"><div class="course-time">${esc(o.time).replace('–','<br>')}</div><div class="course-line"></div><div><strong>${esc(o.course.name)}</strong><p>${esc(o.room)} · ${o.sections.join('、')} 节</p></div></div>`).join(''):empty('今天没有课','','calendar')}</section><section class="panel"><div class="section-title"><h2>最近练习</h2><span class="tiny-label">连续 ${d.streak} 天</span></div>${d.recent.length?d.recent.map(r=>`<button class="history-row" data-act="open-exercise" data-id="${esc(r.exercise)}">${esc(S.catalog.find(e=>e.id===r.exercise)?.title||'写作练习')}<small>${timeLabel(r.created)}</small></button>`).join(''):empty('暂无记录','','pen')}</section></div>`);
}
async function loadWriting(rid){
  const [catalog, d] = await Promise.all([api('/api/exercises'),api('/api/dashboard')]);
  if(rid!==S.renderId)return;S.catalog=catalog;S.dashboard=d;
  let eid=S.routeId;
  if(!catalog.some(e=>e.id===eid)){
    eid=d.activeDraft || catalog.find(e=>!e.attemptCount)?.id || catalog[0].id;
    history.replaceState(null,'',`#/write/${eid}`);S.routeId=eid;
  }
  const e=await api('/api/exercises/'+eid);
  if(rid!==S.renderId)return;
  S.exercise=e;S.dirty=false;S.draftConflict=false;
  const cached=stored('draft:'+eid);
  S.localRecovery=cached && cached.text!==e.draft.value.text ? cached:null;
  S.draftText=e.draft.value.text;S.draftRevision=e.draft.revision;
  renderWriting();
  if(e.pending)watchJob(e.pending.id, 'review');
}
function filteredExercises(){return S.catalog.filter(e=>(!S.level||e.level===S.level)&&(!S.category||e.category===S.category));}
function renderWriting(){
  const e=S.exercise,attempt=e.attempts?.[0],num=S.catalog.findIndex(x=>x.id===e.id)+1;
  setView(pageHead('写作练习','',btn('题库','exercise-library',{cls:'secondary small',icon:'list'}))+`
    <div class="toolbar"><select id="exercise-level" aria-label="练习难度"><option value="">循序渐进</option>${Object.entries(levels).map(([k,v])=>`<option value="${k}" ${S.level===k?'selected':''}>${v}</option>`).join('')}</select><select id="exercise-category" aria-label="写作主题"><option value="">所有表达场景</option>${categories.map(c=>`<option ${S.category===c?'selected':''}>${c}</option>`).join('')}</select><span class="grow"></span>${btn('下一题','next-exercise',{cls:'ghost',icon:'arrow'})}</div>
    <div class="writing-grid"><div><section class="prompt-card"><div class="flex between"><div class="flex wrap"><span class="tag green">${esc(e.category)}</span><span class="tag">${levels[e.level]}</span></div><span class="task-number">${String(num).padStart(2,'0')} / ${S.catalog.length}</span></div><h2>${esc(e.zh)}</h2><div class="source-line">${icon('book',true)}灵感来自 ${esc(e.paper.short)} · ${esc(e.paper.venue)} <span>·</span><button class="btn ghost small" data-act="paper-source" data-id="${e.paperId}">查看来源 ${icon('external',true)}</button></div></section>
    <section class="panel editor-panel"><div class="editor-label"><span>Your English version</span><small id="draft-state">已同步</small></div>${S.localRecovery?`<div class="error-banner">发现此设备尚未同步的草稿。${btn('查看并恢复','recover-draft',{cls:'ghost small'})}</div>`:''}<textarea id="translation" class="translation-input" maxlength="12000" aria-label="你的英文译文" spellcheck="true" placeholder="输入英文译文">${esc(S.draftText)}</textarea><div class="editor-footer"><small id="word-count">${countWords(S.draftText)} words</small><div class="flex">${btn('一点提示','hint',{cls:'ghost small',icon:'eye'})}${btn(attempt?'点评这次重写':'请老师点评','submit-review',{icon:'spark',disabled:!!e.pending})}</div></div><div id="hint-area"></div></section>
    <div id="job-state">${e.pending?busyHtml('正在读你的译文，稍后给出具体反馈…'):''}</div><div id="feedback">${attempt?feedbackHtml(attempt):`${btn('查看参考表达','reference',{cls:'ghost small',icon:'book'})}`}</div>
    </div><aside class="writing-aside"><section class="aside-card">${btn('问写作教练','ask-about-exercise',{cls:'secondary small',icon:'chat'})}</section><section class="aside-card"><h3>这道题的足迹</h3>${e.attempts.length?e.attempts.map((a,i)=>`<button class="history-row" data-act="show-attempt" data-id="${a.id}">${i===0?'最近一次':'查看更早练习'}<small>${timeLabel(a.created)} · ${totalScore(a.feedback)}/100</small></button>`).join(''):'<p>暂无点评</p>'}</section></aside></div>`);
}
const countWords=t=>(t.trim().match(/\S+/g)||[]).length;
const totalScore=f=>Math.round(Object.values(f.scores).reduce((a,b)=>a+b,0)*5);
function richText(source){
  const inline=t=>esc(t).replace(/\*\*([^*]+)\*\*/g,'<strong>$1</strong>').replace(/`([^`]+)`/g,'<code>$1</code>');
  return String(source||'').split(/\n\s*\n/).map(block=>{
    const lines=block.trim().split('\n');
    if(lines.length>1&&lines[0].includes('|')&&/^\s*\|?\s*:?[-]+/.test(lines[1])){
      const cells=l=>l.replace(/^\s*\||\|\s*$/g,'').split('|');
      return `<div class="deep-table"><table><thead><tr>${cells(lines[0]).map(c=>`<th>${inline(c)}</th>`).join('')}</tr></thead><tbody>${lines.slice(2).map(l=>`<tr>${cells(l).map(c=>`<td>${inline(c)}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
    }
    return `<p>${lines.map(l=>inline(l.replace(/^#{1,4}\s*/,''))).join('<br>')}</p>`;
  }).join('');
}
const busyHtml=t=>`<div class="busy"><span class="spinner"></span><span>${t}<small style="display:block">可以离开页面，结果会保存在服务器。</small></span></div>`;
function feedbackHtml(a){
  const f=a.feedback;
  return `<section class="panel feedback-card"><div class="flex between wrap"><h2>这一次，学会一点。</h2><span class="tag green">${totalScore(f)} / 100</span></div><div class="score-grid">${[['meaning','语义'],['grammar','语法'],['academic','学术表达'],['clarity','清晰度']].map(([k,l])=>`<div class="score"><strong>${f.scores[k]}<small>/5</small></strong>${l}</div>`).join('')}</div><p class="feedback-summary">${esc(f.summary)}</p>${f.strengths.map(s=>`<p class="help-text">${icon('check',true)} ${esc(s)}</p>`).join('')}<div class="feedback-section"><h3>重点改这几处</h3>${f.corrections.length?f.corrections.map(c=>`<div class="correction"><span class="tag orange">${esc(c.category)}</span><div class="spacer" style="height:8px"></div><div class="before">${esc(c.original)}</div><div class="after">${esc(c.revised)}</div><p>${esc(c.reason)}</p></div>`).join(''):'<p class="help-text">这次没有需要纠正的具体错误，继续保持。</p>'}</div><div class="feedback-section"><h3>一种自然的表达</h3><div class="reference">${esc(f.polished)}</div><div class="feedback-body">${esc(f.explanation)}</div></div><div class="feedback-section"><h3>带走这些通用表达 <span class="tag">已自动存入积累本</span></h3>${f.patterns.map(p=>`<div class="mini-note"><strong>${esc(p.pattern)}</strong><p>${esc(p.meaning)}</p><p>${esc(p.example)}</p><p>${esc(p.pitfall)}</p></div>`).join('')}${f.vocabulary.map(v=>`<div class="mini-note"><strong>${esc(v.term)}</strong><p>${esc(v.meaning)}</p><p>${esc(v.example)}</p></div>`).join('')}</div><div class="hint"><strong>轮到你再试一次</strong><br>${esc(f.next_step)}</div><div class="flex between wrap">${btn('回到译文，动手重写','rewrite',{icon:'pen'})}${btn('下一题','next-exercise',{cls:'secondary',icon:'arrow'})}</div><p class="help-text">AI 教学建议供练习参考 · ${esc(a.model)} · ${timeLabel(a.created||new Date().toISOString())}</p></section>`;
}
function draftChanged(){S.draftText=$('#translation').value;S.dirty=true;store('draft:'+S.exercise.id,{text:S.draftText,revision:S.draftRevision,time:Date.now()});$('#word-count').textContent=countWords(S.draftText)+' words';$('#draft-state').textContent='保存中…';clearTimeout(draftTimer);draftTimer=setTimeout(()=>saveDraft().catch(e=>{if($('#draft-state'))$('#draft-state').textContent=e.status===409?'同步冲突 · 本机草稿已保留':'仅保存到此设备';if(e.status===409){S.draftConflict=true;toast(e.message);}}),700);}
async function saveDraft(){
  clearTimeout(draftTimer);
  if(S.draftSaving){await S.draftSaving;if(S.dirty)return saveDraft();return;}
  if(!S.dirty||!S.exercise)return;
  if(S.draftConflict)throw new Error('另一设备更新了草稿，请先解决同步冲突。可复制当前译文后重新打开本题。');
  const eid=S.exercise.id,t=S.draftText,revision=S.draftRevision;
  S.draftSaving=api('/api/drafts/'+eid,{text:t,revision}).then(r=>{
    if(S.exercise?.id===eid){S.draftRevision=r.revision;S.exercise.draft=r;S.dirty=S.draftText!==t;if(S.dirty)store('draft:'+eid,{text:S.draftText,revision:r.revision,time:Date.now()});else{removeStored('draft:'+eid);if($('#draft-state'))$('#draft-state').textContent='已同步';}}
  }).catch(e=>{if(e.status===409)S.draftConflict=true;throw e;}).finally(()=>{S.draftSaving=null;});
  await S.draftSaving;if(S.dirty)return saveDraft();
}
async function watchJob(jid,kind){
  if(pollers.has(jid))return;
  pollers.set(jid,true);
  try{
    while(pollers.has(jid)){
      const job=await api('/api/jobs/'+jid);
      if(['done','failed'].includes(job.status)){
        if(kind==='review'){
          if(S.page==='write'&&S.exercise?.id===job.request.exerciseId){
            if(job.status==='done'){
              S.exercise=await api('/api/exercises/'+job.request.exerciseId);
              S.exercise.pending=null;
              if($('#job-state'))$('#job-state').innerHTML='';
              if($('#feedback')){$('#feedback').innerHTML=feedbackHtml(S.exercise.attempts[0]);$('#feedback').scrollIntoView({behavior:'smooth',block:'start'});}
              $('[data-act="submit-review"]')?.removeAttribute('disabled');
            }else{if($('#job-state'))$('#job-state').innerHTML=`<div class="error-banner">${esc(job.error)}</div>`;$('[data-act="submit-review"]')?.removeAttribute('disabled');}
          }
          toast(job.status==='done'?'点评已完成，表达已自动加入积累本。':job.error);
        }else if(kind==='ask'&&S.page==='chat'){renderChat(await api('/api/chat'));}
        else if(kind==='probe'){toast(job.status==='done'?'模型检测完成':job.error);if(S.page==='settings')await render();}
        break;
      }
      await new Promise(r=>setTimeout(r,1500));
    }
  }catch(e){toast(e.message);}finally{pollers.delete(jid);}
}
function modal(title,body){const d=$('#modal');d.innerHTML=`<div class="modal-head"><h2>${title}</h2>${ibtn('close','关闭弹窗','close-modal')}</div><div class="modal-body">${body}</div>`;if(!d.open)d.showModal();}
function exerciseLibrary(){const items=filteredExercises();modal('挑一个表达，开始练习',`<p class="help-text">${items.length} 道符合当前筛选的练习 · 题目来自本地题库</p>${items.map(e=>`<button class="exercise-option" data-act="choose-exercise" data-id="${e.id}"><span class="tag ${e.attemptCount?'green':''}">${e.attemptCount?'练过 '+e.attemptCount+' 次':levels[e.level]}</span> ${esc(e.title)}<small>${esc(e.zh)}</small></button>`).join('')||empty('这个筛选下还没有题目')}`);}
async function nextExercise(){const list=filteredExercises();if(!list.length){toast('当前筛选下没有题目');return;}const unseen=list.find(e=>!e.attemptCount&&e.id!==S.exercise?.id);const idx=list.findIndex(e=>e.id===S.exercise?.id);await go('write',unseen?.id||list[(idx+1)%list.length].id);}
async function submitReview(){
  if(!S.draftText.trim()){toast('先写下你的英文版本，再请老师点评。');$('#translation').focus();return;}
  await saveDraft();
  const button=$('[data-act="submit-review"]');button.disabled=true;
  try{const job=await api('/api/review',{requestId:uid(),exerciseId:S.exercise.id,translation:S.draftText});$('#job-state').innerHTML=busyHtml('正在读你的译文，稍后给出具体反馈…');watchJob(job.id,'review');}
  catch(e){button.disabled=false;throw e;}
}

async function loadWords(rid){
  if(S.wordMode==='study'){
    const [queue,settings]=await Promise.all([api('/api/vocabulary/next'),api('/api/settings')]);
    if(rid!==S.renderId)return;S.wordQueue=queue;S.word=queue.word;S.wordRevealed=!!queue.revealed;S.wordZh=false;S.settings=settings;renderWords();
    if(S.word&&settings.value.autoSpeak)speak(S.word.word,true);
  }else{await loadWordList(rid);}
}
function wordsHead(){return pageHead('单词学习')+`<div class="toolbar"><div class="tabs"><button data-act="word-mode" data-id="study" class="${S.wordMode==='study'?'active':''}">今日学习</button><button data-act="word-mode" data-id="library" class="${S.wordMode==='library'?'active':''}">我的词库</button></div></div>`;}
function examplesHtml(word){const example=word.senses.flatMap(s=>s.examples||[])[0];if(!example)return '';const escaped=esc(example);const target=esc(word.word).replace(/[.*+?^${}()|[\]\\]/g,'\\$&');return `<div class="example">“${escaped.replace(new RegExp(`\\b(${target})\\b`,'gi'),'<strong>$1</strong>')}”</div>`;}
function wordCard(w,revealed=false,detail=false){
  const p=w.progress||{},known=p.recognition||0;
  return `<section class="word-card"><div class="word-toolbar"><span class="tag">${esc(w.level)} · ${p.difficult?'重难词':p.familiar?'熟词':p.last?'复习词':'新词'}</span><div class="flex">${ibtn('translate','显示或隐藏中文','word-chinese',w.id,S.wordZh)}${ibtn('star',p.favorite?'取消收藏':'收藏单词','word-favorite',w.id,p.favorite)}${ibtn('audio','朗读单词','speak',w.word)}</div></div><div class="word-center"><div class="word-heading"><h2>${esc(w.word)}</h2><span class="recognition" aria-label="认知程度 ${known}/3">${[1,2,3].map(n=>`<i class="${known>=n?'on':''}"></i>`).join('')}</span></div>${w.ipa?`<p class="ipa">/${esc(w.ipa)}/</p>`:''}${S.wordZh?`<p class="hint">${esc(w.zh||'该词暂无中文释义，可查看英文解释或向教练提问。')}</p>`:''}${examplesHtml(w)}</div>${revealed?`<div class="definition">${w.senses.slice(0,detail?30:3).map(s=>`<p><span class="pos">${esc(s.pos)}.</span>${esc(s.definition)}</p>${detail?(s.examples||[]).map(e=>`<p class="muted" style="font-size:12px">${esc(e)}</p>`).join(''):''}`).join('')}${w.deep?`<details><summary class="help-text">展开深度学习笔记（本地预制内容）</summary><p class="help-text">教学笔记 ${w.deep.model?'· '+esc(w.deep.model):''}</p>${w.deep.blocks.map(b=>`<div class="mini-note"><strong>${esc(b.title)}</strong><div class="deep-content">${richText(b.en)}</div>${S.wordZh?`<div class="deep-content hint">${richText(b.zh)}</div>`:''}</div>`).join('')}</details>`:''}${!detail?btn('全部词义 / 拼写','word-detail',{id:w.id,cls:'ghost small',icon:'book'}):''}</div>`:''}</section>`;
}
function renderWords(){
  const w=S.word,q=S.wordQueue;
  setView(wordsHead()+`<div class="word-stage">${w?`<div class="flex between" style="margin-bottom:13px"><span class="tiny-label">${q.review} 个待复习 · ${q.new} 个新词</span><span class="tiny-label">今日已学 ${q.learnedToday} 个新词</span></div>${wordCard(w,S.wordRevealed)}<div class="word-controls">${S.wordRevealed?btn('下一词','next-word',{icon:'arrow'}):`${btn('不认识','rate-word',{id:'unknown',cls:'unknown'})}${btn('有点模糊','rate-word',{id:'fuzzy',cls:'fuzzy'})}${btn('认识','rate-word',{id:'known',icon:'check'})}`}</div><div class="flex between" style="margin-top:14px">${btn('已经很熟，跳过','word-familiar',{cls:'ghost small'})}${btn('问这个词的用法','ask-word',{cls:'ghost small',icon:'chat'})}</div>`:`<section class="panel">${empty('今日词卡完成','','check')}<div class="flex" style="justify-content:center">${btn('去写一句英文','start-writing',{icon:'pen'})}${btn('浏览词库','word-mode',{id:'library',cls:'secondary'})}</div></section>`}</div>`);
}
async function loadWordList(rid=S.renderId){
  const q=new URLSearchParams({q:S.wordQuery,level:S.wordLevel,filter:S.wordFilter,offset:S.wordOffset});
  const d=await api('/api/vocabulary?'+q);if(rid!==S.renderId)return;S.wordList=d;
  setView(wordsHead()+`<div class="toolbar"><input id="word-search" class="notebook-search" placeholder="搜索 10,000 个单词…" aria-label="搜索单词" value="${esc(S.wordQuery)}"><select id="word-level" aria-label="词汇级别"><option value="">全部级别</option>${['L1','L2','L3','L4'].map(l=>`<option ${S.wordLevel===l?'selected':''}>${l}</option>`).join('')}</select><select id="word-filter" aria-label="词库分类">${[['','全部词库'],['favorite','我的收藏'],['difficult','重难词'],['familiar','熟词本'],['due','待复习']].map(([v,l])=>`<option value="${v}" ${S.wordFilter===v?'selected':''}>${l}</option>`).join('')}</select></div><p class="help-text">共 ${d.total} 个单词</p><div class="word-list">${d.items.map(w=>`<button data-act="word-detail" data-id="${esc(w.id)}"><div class="grow"><strong>${esc(w.word)}</strong><small>${w.level}${w.progress.favorite?' · 已收藏':''}${w.progress.familiar?' · 熟词':''}</small></div><span class="recognition" aria-label="认知程度 ${w.progress.recognition||0}/3">${[1,2,3].map(n=>`<i class="${(w.progress.recognition||0)>=n?'on':''}"></i>`).join('')}</span>${icon('right',true)}</button>`).join('')}</div>${!d.items.length?empty('没有找到匹配的词','试试其他拼写或分类。','search'):''}<div class="pagination">${btn('上一页','word-page',{id:'-1',cls:'secondary small',disabled:S.wordOffset===0})}<span class="tiny-label">${Math.floor(S.wordOffset/60)+1} / ${Math.max(1,Math.ceil(d.total/60))}</span>${btn('下一页','word-page',{id:'1',cls:'secondary small',disabled:S.wordOffset+60>=d.total})}</div>`);
}
async function showWord(id){S.detailWord=await api('/api/vocabulary/'+encodeURIComponent(id));S.wordZh=false;modal('词汇卡片',wordCard(S.detailWord,true,true)+`<div class="divider"></div><label class="field">拼写一下 <input id="spelling-input" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="输入拼写"></label><div id="spelling-result"></div><div class="form-actions">${btn('检查拼写','check-spelling',{cls:'secondary'})}${btn('重新加入复习','word-relearn',{id,cls:'secondary'})}${btn('问教练用法','ask-word',{id,icon:'chat'})}</div>`);}
function speak(word,automatic=false){
  if(window.StudyDeskNative?.speak){window.StudyDeskNative.speak(word);return;}
  if(!('speechSynthesis' in window)){if(!automatic)toast('当前浏览器不支持朗读，请使用 Android App 或支持语音的浏览器。');return;}
  speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(word);u.lang='en-US';u.rate=.85;
  const voice=speechSynthesis.getVoices().find(v=>v.lang.startsWith('en')&&v.localService)||speechSynthesis.getVoices().find(v=>v.lang.startsWith('en'));if(voice)u.voice=voice;
  u.onerror=()=>{if(!automatic)toast('无法播放语音，请检查设备英语语音包或先点一下朗读按钮。');};speechSynthesis.speak(u);
}
async function wordAction(w,rating){const p=await api('/api/vocabulary/'+encodeURIComponent(w.id),{requestId:uid(),rating,revision:w.progress?.revision||0});w.progress=p;if(S.word?.id===w.id)S.word.progress=p;if(S.detailWord?.id===w.id)S.detailWord.progress=p;return p;}
async function rateWord(rating){$$('[data-act="rate-word"]').forEach(b=>b.disabled=true);try{await wordAction(S.word,rating);S.wordRevealed=true;renderWords();}catch(e){$$('[data-act="rate-word"]').forEach(b=>b.disabled=false);throw e;}}

function renderNotebook(){
  const kinds={all:'全部积累',mistake:'错题',vocabulary:'词汇',pattern:'句式',due:'待复习',mastered:'已掌握'};
  const today=S.dashboard?.today||new Date(Date.now()+8*3600000).toISOString().slice(0,10);
  const notes=S.notebook.filter(n=>(S.noteFilter==='all'||n.kind===S.noteFilter||(S.noteFilter==='due'&&!n.mastered&&n.due<=today)||(S.noteFilter==='mastered'&&n.mastered))&&JSON.stringify(n).toLowerCase().includes(S.noteQuery.toLowerCase())).sort((a,b)=>Number(b.pinned)-Number(a.pinned));
  setView(pageHead('我的积累')+`<div class="toolbar"><input id="note-search" class="notebook-search" aria-label="搜索积累" placeholder="搜索句式、词汇或自己的备注…" value="${esc(S.noteQuery)}"><select id="note-filter" aria-label="积累分类">${Object.entries(kinds).map(([v,l])=>`<option value="${v}" ${S.noteFilter===v?'selected':''}>${l}</option>`).join('')}</select><span class="grow"></span><span class="tiny-label">${notes.length} 条积累</span></div><div class="notebook-grid">${notes.map(n=>`<article class="note-card ${n.mastered?'mastered':''}"><div class="flex between"><span class="tag ${n.kind==='mistake'?'orange':n.kind==='pattern'?'green':'purple'}">${kinds[n.kind]}</span><div class="flex">${!n.mastered&&n.due<=today?'<span class="tiny-label">该复习了</span>':''}${ibtn('star',n.pinned?'取消置顶':'置顶这条积累','note-pin',n.id,n.pinned)}</div></div><h3>${esc(n.title)}</h3>${n.original?`<div class="correction" style="padding:0;border:0"><div class="before">${esc(n.original)}</div></div>`:''}<p>${esc(n.reason||n.meaning||'')}</p>${n.example?`<div class="example-small">${esc(n.example)}</div>`:''}${n.pitfall?`<p>使用提醒：${esc(n.pitfall)}</p>`:''}${n.personalNote?`<div class="hint">我的备注：${esc(n.personalNote)}</div>`:''}<div class="note-footer">${btn('再练原题','open-exercise',{id:n.exerciseId,cls:'ghost small',icon:'pen'})}<div class="flex">${ibtn('pen','添加自己的备注','note-edit',n.id)}${btn(n.mastered?'重新复习':'复习一下','note-review',{id:n.id,cls:'secondary small'})}</div></div></article>`).join('')}</div>${!notes.length?`<section class="panel">${empty('暂无积累','','notes')}<div class="flex" style="justify-content:center">${btn('去写作练习','start-writing',{icon:'arrow'})}</div></section>`:''}`);
}
function reviewNote(id){const n=S.notebook.find(n=>n.id===id);S.reviewNote=n;modal('先回忆，再揭晓',`<span class="tag">${n.kind==='mistake'?'修改这个表达':n.kind==='pattern'?'回忆通用句式':'回忆英文搭配'}</span><p style="font-size:18px;margin:20px 0;line-height:1.9">${esc(n.original||n.meaning)}</p><div id="note-answer">${btn('看看答案','reveal-note',{icon:'eye',cls:'secondary'})}</div>`);}
function revealNote(){const n=S.reviewNote;$('#note-answer').innerHTML=`<div class="reference">${esc(n.title)}</div><p class="help-text">${esc(n.reason||n.pitfall||n.example||'')}</p><div class="form-actions">${btn('还需要再看','note-rate',{id:'unknown',cls:'secondary'})}${btn('这次记住了','note-rate',{id:'known',icon:'check'})}</div>`;}
async function updateNote(id,body){const n=S.notebook.find(n=>n.id===id);const updated=await api('/api/notebook/'+id,{...body,revision:n.revision});S.notebook=S.notebook.map(x=>x.id===id?updated:x);renderNotebook();}

const schedulePalette=[
  ['#ec799b','#e26e93'],['#a6bced','#98afe1'],['#65c9c8','#53bcbc'],
  ['#e99178','#e48670'],['#73b9e7','#64aee0'],['#b7ace8','#a99bda'],
  ['#659bc8','#598dbc'],['#e9a3bb','#dc91af']
];
function courseColor(name){
  const named=[['材料科学',0],['分布式系统',1],['日语二外',2],['数据库前沿',3],['马克思主义',4],['深度学习',5],['工程伦理',6],['数据库系统',7],['体育',5]];
  const match=named.find(([part])=>name.includes(part));
  return schedulePalette[match?match[1]:[...name].reduce((n,c)=>(n*31+c.charCodeAt(0))%10000,0)%schedulePalette.length];
}
function placeScheduleCourses(entries){
  for(const day of [...new Set(entries.map(e=>e.date))]){
    const sorted=entries.filter(e=>e.date===day).sort((a,b)=>a.start-b.start||b.end-a.end);
    let group=[],groupEnd=0;
    const finish=()=>{
      if(!group.length)return;
      const occupied=[];
      for(const entry of group){
        let slot=occupied.findIndex(end=>end<entry.start);
        if(slot<0)slot=occupied.length;
        occupied[slot]=entry.end;
        entry.slot=slot;
      }
      const count=1+Math.max(...group.map(e=>e.slot));
      group.forEach(e=>e.slotCount=count);
    };
    for(const entry of sorted){
      if(group.length&&entry.start>groupEnd){finish();group=[];}
      group.push(entry);groupEnd=Math.max(groupEnd,entry.end);
    }
    finish();
  }
}
function renderSchedule(){
  const d=S.schedule,today=new Date(Date.now()+8*3600000).toISOString().slice(0,10);
  if(!S.selectedWeekday)S.selectedWeekday=(new Date(today+'T12:00:00+08:00').getUTCDay()||7);
  const selectedDate=d.days[S.selectedWeekday-1],shortDays='一二三四五六日';
  const entries=d.items.map((o,index)=>({...o,index,start:o.sections[0],end:o.sections.at(-1)}));
  placeScheduleCourses(entries);
  const headers=d.days.map((date,i)=>`<button type="button" class="week-day ${i+1===S.selectedWeekday?'selected':''} ${date===today?'today':''}" style="grid-column:${i+2}" data-act="select-weekday" data-id="${i+1}" aria-pressed="${i+1===S.selectedWeekday}" aria-label="周${shortDays[i]} ${Number(date.slice(5,7))}月${Number(date.slice(8))}日${d.holidays.includes(date)?'，停课日':''}"><span>${shortDays[i]}</span><strong>${Number(date.slice(8))}</strong>${d.holidays.includes(date)?'<em aria-hidden="true">休</em>':''}</button>`).join('');
  const axis=d.times.map((time,i)=>`<div class="week-period" style="grid-row:${i+2}"><strong>${i+1}</strong><small>${esc(time.split('-')[0])}<br>${esc(time.split('-')[1])}</small></div>`).join('');
  const lanes=d.days.map((date,i)=>`<div class="week-lane ${i+1===S.selectedWeekday?'selected':''}" style="grid-column:${i+2}" aria-hidden="true"></div>`).join('');
  const cards=entries.map(o=>{
    const [start,end]=courseColor(o.course.name),rowSpan=o.end-o.start+1;
    return `<button type="button" class="week-course" style="grid-column:${d.days.indexOf(o.date)+2};grid-row:${o.start+1}/span ${rowSpan};--slot-left:${o.slot/o.slotCount*100}%;--slot-width:${100/o.slotCount}%;--course-start:${start};--course-end:${end}" data-act="course-detail" data-id="${o.index}" aria-label="${esc(o.course.name)}，${o.sections.join('、')} 节${o.room?'，'+esc(o.room):''}" title="${esc(o.course.name)} · ${o.sections.join('、')} 节${o.room?' · '+esc(o.room):''}"><strong>${esc(o.course.name)}</strong>${o.room?`<small class="course-room">@${esc(o.room)}</small>`:''}${o.conflicts>1?'<span class="conflict-dot" aria-label="课程冲突">!</span>':''}</button>`;
  }).join('');
  const current=d.days.includes(today);
  setView(`<section class="schedule-screen"><div class="schedule-heading"><div class="schedule-heading-main"><h1><span class="schedule-week-select"><select id="schedule-week" aria-label="选择教学周">${Array.from({length:18},(_,i)=>`<option value="${i+1}" ${d.week===i+1?'selected':''}>第${i+1}周</option>`).join('')}</select></span><span>周${shortDays[S.selectedWeekday-1]}</span></h1><div class="schedule-subdate">${Number(selectedDate.slice(0,4))}/${Number(selectedDate.slice(5,7))}/${Number(selectedDate.slice(8))}<span class="schedule-week-status ${current?'current':''}">${current?'本周':'非本周'}</span></div></div><div class="schedule-head-actions">${ibtn('clock','回到本周','current-week')}${ibtn('list','管理课程','manage-courses')}${ibtn('settings','设置','nav','settings')}</div></div>
    <div class="week-board" aria-label="第 ${d.week} 周课表"><div class="week-grid"><div class="week-corner">${Number(d.days[0].slice(5,7))}<span>月</span></div>${headers}${lanes}${axis}${cards}</div></div></section>`);
  const board=$('.week-board');let start=null;
  board.addEventListener('touchstart',event=>{if(event.touches.length===1)start={x:event.touches[0].clientX,y:event.touches[0].clientY};},{passive:true});
  board.addEventListener('touchend',event=>{if(!start||!event.changedTouches.length)return;const dx=event.changedTouches[0].clientX-start.x,dy=event.changedTouches[0].clientY-start.y;start=null;if(Math.abs(dx)>55&&Math.abs(dx)>Math.abs(dy)*1.3){const next=Math.max(1,Math.min(18,S.week+(dx<0?1:-1)));if(next!==S.week){S.week=next;render().catch(error=>toast(error.message));}}},{passive:true});
}
function courseDetail(index){
  const o=S.schedule.items[Number(index)];S.selectedOccurrence=o;const c=o.course;
  modal('课程详情',`<div class="course-detail"><span class="tag">${o.date} · 第 ${S.schedule.week} 周</span><h2>${esc(c.name)}</h2><dl><dt>时间</dt><dd>${o.time}（${o.sections.join('、')} 节）</dd><dt>教室</dt><dd>${esc(o.room)||'待定'}</dd><dt>教师</dt><dd>${esc(c.teachers.join('、'))}</dd><dt>班级</dt><dd>${esc(c.className)}</dd><dt>备注</dt><dd>${esc(c.note)||'无'}</dd></dl>${o.conflicts>1?'<p class="error-banner">这个时段还有重叠课程，请在课表中分别查看。</p>':''}</div><div class="form-actions">${btn('编辑常规课程','edit-course',{id:c.id,cls:'secondary'})}${btn('调整这一次','adjust-course',{icon:'calendar'})}</div>`);
}
function manageCourses(){const s=S.schedule.value;modal('课程与单次调整',`${s.courses.map(c=>`<button class="exercise-option" data-act="edit-course" data-id="${esc(c.id)}">${esc(c.name)}<small>周${'一二三四五六日'[c.weekday-1]} · ${c.sections.join('、')} 节 · 第 ${c.weeks.join('、')} 周</small></button>`).join('')}<div class="divider"></div><h3>单次调整（${s.adjustments.length}）</h3>${s.adjustments.map((a,i)=>`<div class="paper-row"><strong>${esc(s.courses.find(c=>c.id===a.courseId)?.name)}</strong><p class="help-text">${a.originalDate} → ${a.cancelled?'取消':a.date+' · '+a.sections.join('、')+'节'}</p>${btn('恢复原安排','restore-adjustment',{id:String(i),cls:'secondary small'})}</div>`).join('')||'<p class="help-text">还没有单次调整。</p>'}<div class="form-actions">${btn('导入课表','import-courses',{cls:'secondary',icon:'upload'})}${btn('新增课程','add-course',{icon:'plus'})}</div>`);}
function numberRange(v,max){const out=[];for(const part of v.split(/[,，、\s]+/).filter(Boolean)){const m=/^(\d+)(?:[-–](\d+))?$/.exec(part);if(!m)throw new Error('使用数字或范围，例如 1-8、10、12');const lo=+m[1],hi=+(m[2]||m[1]);if(lo<1||hi>max||lo>hi)throw new Error(`范围应在 1–${max} 之间`);for(let n=lo;n<=hi;n++)out.push(n);}if(!out.length)throw new Error('请填写节次或周次');return [...new Set(out)].sort((a,b)=>a-b);}
function editCourse(id=''){
  const c=S.schedule.value.courses.find(c=>c.id===id)||{name:'',room:'',className:'1班',teachers:[],weekday:1,sections:[1,2],weeks:Array.from({length:16},(_,i)=>i+1),note:''};S.editCourseId=id;
  modal(id?'编辑课程':'添加课程',`<form id="course-form"><div class="form-grid"><label class="field full">课程名称<input name="name" required maxlength="500" value="${esc(c.name)}"></label><label class="field">星期<select name="weekday">${[1,2,3,4,5,6,7].map(n=>`<option value="${n}" ${n===c.weekday?'selected':''}>星期${'一二三四五六日'[n-1]}</option>`).join('')}</select></label><label class="field">节次<input name="sections" required value="${c.sections.join(',')}" placeholder="1-2"></label><label class="field full">周次（支持范围、逗号）<input name="weeks" required value="${c.weeks.join(',')}" placeholder="1-8,10,12"></label><label class="field">教室<input name="room" value="${esc(c.room)}" maxlength="500"></label><label class="field">班级<input name="className" value="${esc(c.className)}" maxlength="500"></label><label class="field full">教师（逗号分隔）<input name="teachers" value="${esc(c.teachers.join(','))}" maxlength="2000"></label><label class="field full">备注<input name="note" value="${esc(c.note)}" maxlength="500"></label></div><div class="form-actions">${id?btn('删除课程','delete-course',{id,cls:'danger'}):''}<button class="btn" type="submit">保存课程</button></div></form>`);
}
async function saveCourse(form){
  const f=Object.fromEntries(new FormData(form));const c={id:S.editCourseId||'course-'+uid(),name:f.name.trim(),className:f.className.trim(),room:f.room.trim(),teachers:f.teachers.split(/[,，、]/).map(t=>t.trim()).filter(Boolean),weekday:+f.weekday,sections:numberRange(f.sections,11),weeks:numberRange(f.weeks,18),note:f.note.trim()};
  const value=structuredClone(S.schedule.value);const prev=value.courses.findIndex(x=>x.id===c.id);
  if(prev>=0){if(value.adjustments.some(a=>a.courseId===c.id)&&!confirm('修改常规课程会清除这门课的单次调整。确定保存吗？'))return;value.courses[prev]=c;value.adjustments=value.adjustments.filter(a=>a.courseId!==c.id);}else value.courses.push(c);
  await saveSchedule(value);toast('课程已保存，所有设备同步。');
}
async function saveSchedule(value){await api('/api/schedule',{value,revision:S.schedule.revision});$('#modal').close();await render();}
function adjustCourse(memberIndex=0){
  const o=S.selectedOccurrence,m=o.members[memberIndex];S.adjustMember=m;const base=S.schedule.value.courses.find(c=>c.id===m.courseId);const old=S.schedule.value.adjustments.find(a=>a.courseId===m.courseId&&a.originalDate===m.originalDate);
  modal('只调整这一次',`${o.members.length>1?`<label class="field">这张卡片包含多个连堂课段<select id="adjust-member">${o.members.map((m,i)=>{const c=S.schedule.value.courses.find(c=>c.id===m.courseId);return `<option value="${i}" ${i===memberIndex?'selected':''}>原第 ${c.sections.join('、')} 节</option>`;}).join('')}</select></label><div class="spacer"></div>`:''}<p class="help-text">${esc(base.name)} · 原日期 ${m.originalDate}<br>调整不会改变其他周的安排。</p><form id="adjust-form"><div class="form-grid"><label class="field">目标日期<input name="date" type="date" min="2026-09-14" max="2027-01-17" required value="${old?.date||o.date}"></label><label class="field">节次<input name="sections" required value="${(old?.sections||base.sections).join(',')}"></label><label class="field full">教室<input name="room" value="${esc(old?.room??base.room)}" maxlength="500"></label><label class="field full"><span><input name="cancelled" type="checkbox" ${old?.cancelled?'checked':''}> 取消这一次课程</span></label></div><div class="form-actions"><button type="submit" class="btn">保存本次调整</button></div></form>`);
}
async function saveAdjustment(form){const f=Object.fromEntries(new FormData(form)),m=S.adjustMember;const a={...m,date:f.date,sections:numberRange(f.sections,11),room:f.room.trim(),cancelled:f.cancelled==='on'};if(['2026-09-25','2026-10-01','2026-10-02','2026-10-03','2027-01-01'].includes(a.date)&&!a.cancelled&&!confirm('这一天在停课日列表中。仍要主动安排这次课程吗？'))return;const v=structuredClone(S.schedule.value);v.adjustments=v.adjustments.filter(x=>!(x.courseId===m.courseId&&x.originalDate===m.originalDate));v.adjustments.push(a);await saveSchedule(v);toast('这一次安排已更新。');}

function renderSettings(){
  const settings=S.settings.value,profile=S.profile.value,models=S.models,prompts=S.wordPrompts.value.presets;
  const status={available:'可用',unavailable:'已停用',exhausted:'今日额度不足',cooldown:'冷却中',untested:'尚未检测'};
  setView(pageHead('设置')+`<div class="settings-grid"><div>
    <section class="panel"><h2>显示</h2><form id="profile-form"><label class="field" for="display-name">用户名<input id="display-name" name="displayName" maxlength="30" value="${esc(profile.displayName)}" placeholder="可不填"></label><div class="setting-row"><label for="show-name">显示用户名</label><input id="show-name" name="showName" type="checkbox" ${profile.showName?'checked':''}></div><div class="setting-row"><label for="minimal-mode">极简模式</label><input id="minimal-mode" name="minimalMode" type="checkbox" ${profile.minimalMode?'checked':''}></div><button class="btn small" type="submit">保存显示设置</button></form></section>
    <section class="panel" id="word-prompt-settings"><div class="section-title"><h2>单词提问模板</h2>${btn('添加','edit-word-prompt',{cls:'secondary small',icon:'plus'})}</div><div class="prompt-manage-list">${prompts.map(v=>`<div class="prompt-manage-row"><strong>${esc(v.name)}</strong><div class="flex">${ibtn('pen','编辑 '+v.name,'edit-word-prompt',v.id)}${ibtn('close','删除 '+v.name,'delete-word-prompt',v.id)}</div></div>`).join('')||'<p class="help-text">暂无模板</p>'}</div><p class="help-text">从词卡点“问用法”时可选择模板。用 {word} 表示当前单词。</p></section>
    <section class="panel"><h2>学习设置</h2><form id="settings-form"><div class="setting-row"><label for="daily-limit">每日新词</label><input id="daily-limit" name="dailyNewLimit" type="number" min="1" max="100" value="${settings.dailyNewLimit}" required></div><div class="setting-row"><label for="writing-goal">每日写作目标</label><input id="writing-goal" name="writingGoal" type="number" min="1" max="20" value="${settings.writingGoal}" required></div><div class="setting-row"><label for="auto-speak">自动朗读新词</label><input id="auto-speak" name="autoSpeak" type="checkbox" ${settings.autoSpeak?'checked':''}></div><div class="setting-row"><label>新词级别</label></div><div class="check-row">${[['L1','基础'],['L2','高中'],['L3','大学核心'],['L4','进阶']].map(([v,l])=>`<label><input type="checkbox" name="levels" value="${v}" ${settings.levels.includes(v)?'checked':''}>${l}</label>`).join('')}</div><div class="spacer"></div><button class="btn small" type="submit">保存学习设置</button></form></section>
    <section class="panel"><h2>我的数据</h2><div class="flex wrap" style="margin-top:12px"><a class="btn secondary" href="/api/backup" download>${icon('download',true)}导出备份</a>${btn('恢复备份','restore-backup',{cls:'secondary',icon:'upload'})}<input class="hidden" id="backup-file" type="file" accept="application/json,.json"></div></section>
  </div><div>
    <section class="panel"><h2>连接与使用</h2><p class="help-text">服务器：${esc(location.origin)}</p>${window.StudyDeskNative?`<div class="flex wrap">${window.StudyDeskNative.checkForUpdate?btn('检查应用更新','native-update',{cls:'secondary small',icon:'refresh'}):''}${btn('修改服务器地址','native-server',{cls:'secondary small',icon:'settings'})}</div>`:''}<details><summary class="help-text">课表与调课</summary><p class="help-text">七天同时显示；在课表内左右滑动切换教学周，点课程查看详情。单次调课与循环课程分别保存。</p></details><details><summary class="help-text">写作与积累</summary><p class="help-text">先独立翻译，点评后按建议重写。错误、词汇和句式会存入积累本，可以复习与备注。</p></details><details><summary class="help-text">词卡与复习</summary><p class="help-text">先判断认识程度，再看释义；三点表示认知等级。点“下一词”才会切换。跨天遗忘会缩短复习间隔；收藏、熟词和重难词可在词库管理。</p></details><details><summary class="help-text">内容来源与许可</summary><p class="help-text">英文释义来自 Open English WordNet 2025（CC BY 4.0）；选词及分层使用 wordfreq 3.1.1（CC BY-SA 4.0）。部分词没有中文或深度笔记。</p><p class="help-text"><a href="/licenses/CC-BY-4.0.txt" target="_blank" rel="noopener">CC BY 4.0</a> · <a href="/licenses/CC-BY-SA-4.0.txt" target="_blank" rel="noopener">CC BY-SA 4.0</a> · <a href="/licenses/wordfreq-NOTICE.txt" target="_blank" rel="noopener">完整署名</a></p></details>${btn('论文素材来源','paper-library',{cls:'ghost small',icon:'book'})}</section>
    <section class="panel"><div class="section-title"><h2>AI 模型</h2>${btn('重新检测','probe',{cls:'secondary small',icon:'refresh'})}</div>${!models.configured?'<div class="error-banner">尚未配置 Gemini 密钥</div>':''}<div id="probe-state"></div>${models.models.map(v=>`<div class="model-row ${v.status==='unavailable'?'model-disabled':''}"><span class="rank">${String(v.priority).padStart(2,'0')}</span><div class="grow"><strong>${esc(v.name)}</strong><div class="account-lines">${(v.accounts||[]).map(a=>`<span class="${a.status==='available'?'ok':''}" title="${esc(a.message)}">${esc(a.account)} ${a.used}/${a.rpd} · ${status[a.status]||a.status}</span>`).join('')}</div></div><span class="tag ${v.status==='available'?'green':''}">${status[v.status]||esc(v.status)}</span></div>`).join('')}<p class="help-text">计数日：${models.quotaDay} · 下次换日：${timeLabel(models.resetAt)} 北京时间</p><details><summary class="help-text">模型优先级与计数说明</summary><p class="help-text">${esc(models.note)}。先遍历高等级模型的可用密钥，再向下一等级切换。检测也计入调用次数。</p></details></section>
  </div></div>`);
  if(S.focusPromptSettings){S.focusPromptSettings=false;requestAnimationFrame(()=>$('#word-prompt-settings')?.scrollIntoView({behavior:'smooth',block:'start'}));}
}
function editWordPrompt(id=''){
  const old=S.wordPrompts.value.presets.find(p=>p.id===id);
  S.promptEditId=id;
  modal(old?'编辑提示词':'添加提示词',`<form id="word-prompt-form"><label class="field">名称<input name="name" required maxlength="40" value="${esc(old?.name||'')}"></label><div class="spacer"></div><label class="field">模板<textarea name="template" required maxlength="3800" rows="7" placeholder="请讲解 {word} 的学术用法…">${esc(old?.template||'')}</textarea></label><p class="help-text">必须包含 {word}，提问时会替换成当前单词。</p><div class="form-actions"><button class="btn" type="submit">保存模板</button></div></form>`);
}
async function saveWordPrompt(form){
  const data=new FormData(form),value=structuredClone(S.wordPrompts.value);
  const preset={id:S.promptEditId||'prompt-'+uid(),name:String(data.get('name')||'').trim(),template:String(data.get('template')||'').trim()};
  const index=value.presets.findIndex(p=>p.id===preset.id);
  if(index<0)value.presets.push(preset);else value.presets[index]=preset;
  S.wordPrompts=await api('/api/word-prompts',{value,revision:S.wordPrompts.revision});
  $('#modal').close();renderSettings();toast('提示词已同步。');
}
async function openWordPrompt(w){
  S.wordPrompts=await api('/api/word-prompts');S.questionWord=w.word;
  const prompts=S.wordPrompts.value.presets;
  S.selectedPromptId=prompts.some(p=>p.id===stored('word-prompt-last'))?stored('word-prompt-last'):prompts[0]?.id;
  modal('问单词用法',`<div class="flex between"><strong>${esc(w.word)}</strong>${btn('管理模板','open-prompts',{cls:'ghost small',icon:'settings'})}</div><div class="prompt-list">${prompts.map(p=>`<button type="button" class="prompt-pick ${p.id===S.selectedPromptId?'active':''}" data-act="choose-word-prompt" data-id="${esc(p.id)}"><strong>${esc(p.name)}</strong><small>${esc(p.template.replaceAll('{word}',w.word))}</small></button>`).join('')||'<p class="help-text">先到设置添加提示词模板。</p>'}</div><div class="form-actions">${btn('提问','send-word-prompt',{icon:'chat',disabled:!prompts.length})}</div>`);
}
function renderChat(history){
  S.chatHistory=history;
  setView(pageHead('AI 教练')+`<section class="panel"><div class="chat-shell"><div class="chat-messages" id="chat-messages">${!history.length?`<div class="suggestions">${['什么时候用 a，什么时候用 the？','如何谨慎地表达实验结论？','介绍方法有哪些通用句式？'].map(q=>`<button data-act="suggest-question" data-id="${esc(q)}">${q}</button>`).join('')}</div>`:history.map(h=>`<div class="chat-message user">${esc(h.request.question)}</div>${h.status==='done'?`<div class="chat-message">${esc(h.result.answer)}<small>${esc(h.result.model)}</small></div>`:h.status==='failed'?`<div class="error-banner">${esc(h.error)}</div>`:busyHtml('教练正在整理回答…')}`).join('')}</div><form class="chat-input" id="chat-form"><label class="sr-only" for="chat-question">你的问题</label><textarea id="chat-question" rows="2" maxlength="4000" placeholder="说说你卡在哪里…">${esc(stored('chat-draft')||'')}</textarea><button type="submit" class="btn" ${history.some(h=>['queued','running'].includes(h.status))?'disabled':''}>${icon('arrow')}<span>发送</span></button></form></div></section>`);
  const msgs=$('#chat-messages');msgs.scrollTop=msgs.scrollHeight;
  history.filter(h=>['queued','running'].includes(h.status)).forEach(h=>watchJob(h.id,'ask'));
}
async function sendChat(){const question=$('#chat-question').value.trim();if(!question)return;const b=$('#chat-form button');b.disabled=true;try{const j=await api('/api/ask',{requestId:uid(),question,exerciseId:S.chatExerciseId});removeStored('chat-draft');renderChat(await api('/api/chat'));watchJob(j.id,'ask');}catch(e){b.disabled=false;throw e;}}
async function paperSource(id){const papers=await api('/api/papers'),p=papers.find(p=>p.id===id);modal('论文素材来源',`<span class="tag green">${esc(p.venue)} · ${esc(p.distinction)}</span><h2 style="font-size:21px;margin:16px 0;line-height:1.6">${esc(p.title)}</h2><p class="help-text">${esc(p.authors)}</p><p class="help-text">题目提炼自论文的论述功能，已泛化方法名、模型名和条件，优先练习能迁移到自己研究中的句式。题目与参考表达均为教学改写，不是论文逐字译文或新的实验结论。</p><div class="form-actions"><a class="btn secondary" href="${esc(p.evidence)}" target="_blank" rel="noopener">会议 / 作者说明</a><a class="btn" href="/papers/${p.id}.pdf" target="_blank" rel="noopener">阅读本地 PDF ${icon('external',true)}</a></div>`);}
async function paperLibrary(){const papers=await api('/api/papers');modal('精选论文素材库',`<p class="help-text">${papers.length} 篇公开论文 · 40 道离线练习 · 专注可迁移的表达<br>论文保留作者原有版权；本地副本用于个人学习。</p>${papers.map(p=>`<div class="paper-row"><span class="tag green">${esc(p.venue)} · ${esc(p.distinction)}</span><h3>${esc(p.title)}</h3><p>${esc(p.authors)}</p>${btn('查看来源与原文','paper-source',{id:p.id,cls:'ghost small',icon:'external'})}</div>`).join('')}`);}

async function action(act,id,e){
  if(act==='nav'){await go(id);return;}
  if(act==='reload'){await render();return;}
  if(act==='close-modal'){$('#modal').close();return;}
  if(act==='start-writing'){await go('write');return;}
  if(act==='start-words'){S.wordMode='study';await go('words');return;}
  if(act==='open-exercise'||act==='choose-exercise'){$('#modal').close();await go('write',id);return;}
  if(act==='next-exercise'){await nextExercise();return;}
  if(act==='exercise-library'){exerciseLibrary();return;}
  if(act==='hint'){$('#hint-area').innerHTML=`<div class="hint">${S.exercise.hints.map(esc).join('<br>')}</div>`;return;}
  if(act==='reference'){const r=await api('/api/exercises/'+S.exercise.id+'?reference=1');modal('参考表达，不是唯一答案',`<p class="help-text">接受多种自然译法。看过之后，请关掉答案，用自己的话再写一遍。</p><div class="reference">${esc(r.reference)}</div><div class="mini-note"><strong>${esc(r.pattern)}</strong><p>${r.hints.map(esc).join('<br>')}</p></div>`);return;}
  if(act==='submit-review'){await submitReview();return;}
  if(act==='rewrite'){$('#translation').focus();$('#translation').scrollIntoView({behavior:'smooth',block:'center'});return;}
  if(act==='show-attempt'){const a=S.exercise.attempts.find(a=>a.id===id);modal('回看这一次练习',`<p class="help-text">你的原始译文</p><div class="reference">${esc(a.translation)}</div>${feedbackHtml(a)}`);return;}
  if(act==='recover-draft'){modal('此设备未同步的译文',`<p class="help-text">请比较后决定是否用这份草稿替换当前服务器草稿。</p><div class="reference">${esc(S.localRecovery.text)}</div>${btn('使用这份草稿','use-local-draft',{icon:'check'})}`);return;}
  if(act==='use-local-draft'){$('#translation').value=S.localRecovery.text;$('#modal').close();S.localRecovery=null;draftChanged();return;}
  if(act==='ask-about-exercise'){S.chatExerciseId=S.exercise.id;await go('chat');return;}
  if(act==='paper-source'){await paperSource(id);return;}
  if(act==='paper-library'){await paperLibrary();return;}
  if(act==='word-mode'){S.wordMode=id;S.wordOffset=0;await render();return;}
  if(act==='next-word'){await api('/api/vocabulary/next',{wordId:S.word.id,ratedOn:S.word.progress.last});await loadWords(S.renderId);return;}
  if(act==='rate-word'){await rateWord(id);return;}
  if(act==='speak'){speak(id);return;}
  if(act==='word-chinese'){S.wordZh=!S.wordZh;if($('#modal').open&&S.detailWord){$('.word-card',$('#modal')).outerHTML=wordCard(S.detailWord,true,true);}else renderWords();return;}
  if(act==='word-favorite'){const w=S.detailWord?.id===id&&$('#modal').open?S.detailWord:S.word;await wordAction(w,'favorite');if($('#modal').open){$('.word-card',$('#modal')).outerHTML=wordCard(w,true,true);}else renderWords();return;}
  if(act==='word-familiar'){await wordAction(S.word,'familiar');await loadWords(S.renderId);return;}
  if(act==='word-detail'){await showWord(id);return;}
  if(act==='word-relearn'){await wordAction(S.detailWord,'relearn');$('#modal').close();toast('已加入复习计划，明天或今日未评价时可继续学习。');await render();return;}
  if(act==='word-page'){S.wordOffset=Math.max(0,S.wordOffset+60*Number(id));await loadWordList();return;}
  if(act==='check-spelling'){const v=$('#spelling-input').value.trim().toLowerCase();const w=S.detailWord;const ok=[w.word,...(w.variants||[])].some(s=>s.toLowerCase()===v);$('#spelling-result').innerHTML=`<div class="hint">${ok?'拼写正确，再想一想怎样用它造句。':'再看一眼词头，留意不同的字母。'}</div>`;return;}
  if(act==='ask-word'){const w=id?S.detailWord:S.word;await openWordPrompt(w);return;}
  if(act==='choose-word-prompt'){S.selectedPromptId=id;store('word-prompt-last',id);$$('.prompt-pick',$('#modal')).forEach(button=>button.classList.toggle('active',button.dataset.id===id));return;}
  if(act==='send-word-prompt'){
    const preset=S.wordPrompts.value.presets.find(p=>p.id===S.selectedPromptId);
    if(!preset)throw new Error('请先选择提示词模板');
    const button=$('[data-act="send-word-prompt"]');button.disabled=true;
    try{
      const question=preset.template.replaceAll('{word}',S.questionWord);
      await api('/api/ask',{requestId:uid(),question});
      $('#modal').close();S.chatExerciseId=null;await go('chat');
    }catch(error){button.disabled=false;throw error;}
    return;
  }
  if(act==='open-prompts'){S.focusPromptSettings=true;$('#modal').close();await go('settings');return;}
  if(act==='edit-word-prompt'){editWordPrompt(id);return;}
  if(act==='delete-word-prompt'){
    const current=S.wordPrompts.value.presets.find(p=>p.id===id);
    if(!current||!confirm(`删除“${current.name}”？`))return;
    const value={presets:S.wordPrompts.value.presets.filter(p=>p.id!==id)};
    S.wordPrompts=await api('/api/word-prompts',{value,revision:S.wordPrompts.revision});
    renderSettings();toast('模板已删除。');return;
  }
  if(act==='review-notes'){S.noteFilter='due';await go('notebook');return;}
  if(act==='note-pin'){await updateNote(id,{action:'pin'});return;}
  if(act==='note-review'){reviewNote(id);return;}
  if(act==='reveal-note'){revealNote();return;}
  if(act==='note-rate'){await updateNote(S.reviewNote.id,{action:'review',rating:id});$('#modal').close();toast('复习已记录。');return;}
  if(act==='note-edit'){const n=S.notebook.find(n=>n.id===id);S.editNoteId=id;modal('写下自己的理解',`<form id="note-form"><textarea id="personal-note" rows="5" maxlength="4000" placeholder="这个表达适合用在…">${esc(n.personalNote||'')}</textarea><div class="form-actions"><button class="btn" type="submit">保存备注</button></div></form>`);return;}
  if(act==='week'){S.week=Math.max(1,Math.min(18,S.week+Number(id)));await render();return;}
  if(act==='current-week'){S.week=(await api('/api/dashboard')).week;await render();return;}
  if(act==='select-weekday'){S.selectedWeekday=Number(id);renderSchedule();return;}
  if(act==='course-detail'){courseDetail(id);return;}
  if(act==='manage-courses'){manageCourses();return;}
  if(act==='edit-course'){editCourse(id);return;}
  if(act==='add-course'){editCourse();return;}
  if(act==='adjust-course'){adjustCourse();return;}
  if(act==='delete-course'){if(!confirm('删除这门常规课程及其单次调整？'))return;const v=structuredClone(S.schedule.value);v.courses=v.courses.filter(c=>c.id!==id);v.adjustments=v.adjustments.filter(a=>a.courseId!==id);await saveSchedule(v);return;}
  if(act==='restore-adjustment'){const v=structuredClone(S.schedule.value);v.adjustments.splice(+id,1);await saveSchedule(v);toast('已恢复原安排。');return;}
  if(act==='import-courses'){modal('导入课表 JSON',`<p class="help-text">支持原小程序的课程 JSON 与完整课表备份。校验成功后可预览，再决定是否替换当前课表。</p><textarea id="schedule-json" class="code" aria-label="课表 JSON" placeholder='{"schemaVersion":1,"semesterId":"2026-fall","courses":[…]}'></textarea><div id="import-preview"></div><div class="form-actions">${btn('校验并预览','preview-schedule',{icon:'check'})}</div>`);return;}
  if(act==='preview-schedule'){const v=JSON.parse($('#schedule-json').value);if(v.schemaVersion!==1||v.semesterId!=='2026-fall'||!Array.isArray(v.courses))throw new Error('请使用有效的本学期课表格式');S.importSchedule=v;$('#import-preview').innerHTML=`<div class="hint">共 ${v.courses.length} 条课程、${(v.adjustments||[]).length} 条单次调整。<br>${v.courses.slice(0,10).map(c=>esc(c.name)+' · 周'+'一二三四五六日'[c.weekday-1]).join('<br>')}</div>${btn('确认替换课表','confirm-import-schedule',{cls:'danger'})}`;return;}
  if(act==='confirm-import-schedule'){if(!confirm('用这份课表替换当前全部课程和调整？'))return;await saveSchedule(S.importSchedule);toast('课表导入完成。');return;}
  if(act==='probe'){const job=await api('/api/probe',{requestId:uid()});$('#probe-state').innerHTML=busyHtml('正在逐个检测模型，通常需要一分钟左右…');$('[data-act="probe"]').disabled=true;watchJob(job.id,'probe');return;}
  if(act==='restore-backup'){$('#backup-file').click();return;}
  if(act==='confirm-restore'){const r=await api('/api/restore',{backup:S.restoreData,revision:S.restoreVersion});$('#modal').close();S.version=r.version;toast('备份已恢复。当前模型计数保留。');await render();return;}
  if(act==='suggest-question'){$('#chat-question').value=id;store('chat-draft',id);$('#chat-question').focus();return;}
  if(act==='native-server'){window.StudyDeskNative?.openSettings();return;}
  if(act==='native-update'){window.StudyDeskNative?.checkForUpdate?.();return;}
}
document.addEventListener('click',e=>{const el=e.target.closest('[data-act]');if(!el||el.disabled)return;e.preventDefault();Promise.resolve(action(el.dataset.act,el.dataset.id,e)).catch(err=>toast(err.message));});
document.addEventListener('input',e=>{
  if(e.target.id==='translation')draftChanged();
  if(e.target.id==='chat-question')store('chat-draft',e.target.value);
  if(e.target.id==='word-search'){S.wordQuery=e.target.value;S.wordOffset=0;clearTimeout(searchTimer);searchTimer=setTimeout(()=>loadWordList().then(()=>{const el=$('#word-search');el?.focus();el?.setSelectionRange(el.value.length,el.value.length);}).catch(e=>toast(e.message)),350);}
  if(e.target.id==='note-search'){S.noteQuery=e.target.value;clearTimeout(searchTimer);searchTimer=setTimeout(()=>{renderNotebook();const el=$('#note-search');el.focus();el.setSelectionRange(el.value.length,el.value.length);},250);}
});
document.addEventListener('change',async e=>{
  try{
    const id=e.target.id,v=e.target.value;
    if(id==='exercise-level'){S.level=v;await nextExercise();}
    if(id==='exercise-category'){S.category=v;await nextExercise();}
    if(id==='word-level'||id==='word-filter'){S[id==='word-level'?'wordLevel':'wordFilter']=v;S.wordOffset=0;await loadWordList();}
    if(id==='note-filter'){S.noteFilter=v;renderNotebook();}
    if(id==='schedule-week'){S.week=+v;await render();}
    if(id==='adjust-member'){adjustCourse(+v);}
    if(id==='backup-file'){
      const file=e.target.files[0];if(!file)return;if(file.size>32000000)throw new Error('备份文件超过 32 MB');
      S.restoreData=JSON.parse(await file.text());if(S.restoreData.format!=='studydesk-web'||!S.restoreData.tables)throw new Error('不是有效的 StudyDesk 网页版备份');
      S.restoreVersion=(await api('/api/sync')).version;
      modal('确认恢复这份备份',`<p class="help-text">备份日期：${esc(S.restoreData.exportedAt)}<br>写作记录：${S.restoreData.tables.attempts?.length||0} 条<br>表达积累：${S.restoreData.tables.notebook?.length||0} 条<br>恢复会替换当前学习记录，后台会先保存当前数据库快照。</p><div class="form-actions">${btn('取消','close-modal',{cls:'secondary'})}${btn('确认恢复','confirm-restore',{cls:'danger'})}</div>`);
    }
  }catch(err){toast(err.message);}
});
document.addEventListener('submit',async e=>{
  e.preventDefault();const form=e.target,submit=$('button[type="submit"]',form);if(submit?.disabled)return;if(submit)submit.disabled=true;
  try{
    if(form.id==='course-form')await saveCourse(form);
    if(form.id==='adjust-form')await saveAdjustment(form);
    if(form.id==='settings-form'){
      const f=new FormData(form),value={dailyNewLimit:+f.get('dailyNewLimit'),writingGoal:+f.get('writingGoal'),levels:f.getAll('levels'),autoSpeak:f.get('autoSpeak')==='on'};
      S.settings=await api('/api/settings',{value,revision:S.settings.revision});toast('学习设置已同步。');
    }
    if(form.id==='profile-form'){
      const f=new FormData(form),value={displayName:String(f.get('displayName')||'').trim(),showName:f.get('showName')==='on',minimalMode:f.get('minimalMode')==='on'};
      S.profile=await api('/api/profile',{value,revision:S.profile.revision});applyProfileUI();renderSettings();toast('显示设置已同步。');
    }
    if(form.id==='word-prompt-form')await saveWordPrompt(form);
    if(form.id==='note-form'){await updateNote(S.editNoteId,{action:'edit',text:$('#personal-note').value});$('#modal').close();toast('备注已保存。');}
    if(form.id==='chat-form')await sendChat();
  }catch(err){toast(err.message);}finally{if(submit?.isConnected)submit.disabled=false;}
});
window.addEventListener('hashchange',()=>{clearTimeout(draftTimer);render();window.scrollTo({top:0});});
window.addEventListener('beforeunload',e=>{if(S.dirty){e.preventDefault();e.returnValue='';}});
window.addEventListener('online',()=>{syncStatus(true);if(S.dirty)saveDraft().catch(e=>toast(e.message));});
document.addEventListener('visibilitychange',()=>{if(document.hidden&&S.dirty)saveDraft().catch(()=>{});if(!document.hidden)checkSync();});
async function checkSync(){
  if(document.hidden)return;
  try{const {version,day}=await api('/api/sync');if(version!==S.version){const old=S.version;S.version=version;
    if(S.page==='settings'){
      if(!$('#modal').open&&!$('#profile-form :focus')&&!$('#settings-form :focus')){
        const [profile,settings,prompts,models]=await Promise.all([api('/api/profile'),api('/api/settings'),api('/api/word-prompts'),api('/api/models')]);
        if(S.page==='settings'){S.profile=profile;S.settings=settings;S.wordPrompts=prompts;S.models=models;applyProfileUI();renderSettings();}
      }
    }else{const profile=await api('/api/profile');if(profile.revision!==S.profile?.revision){S.profile=profile;applyProfileUI();}}
    if(old&&S.page==='home'){const d=await api('/api/dashboard');if(S.page==='home'){S.dashboard=d;renderHome(d);}}
    else if(old&&S.page==='schedule'&&!$('#modal').open){const d=await api('/api/schedule?week='+S.week);if(S.page==='schedule'){S.schedule=d;renderSchedule();}}
    else if(old&&S.page==='notebook'&&!$('#modal').open&&!$('#note-search')?.matches(':focus')){S.notebook=await api('/api/notebook');if(S.page==='notebook')renderNotebook();}
    else if(old&&S.page==='write'&&!S.dirty&&!S.draftSaving&&S.exercise&&!$('#translation')?.matches(':focus')){
      const currentId=S.exercise.id;const d=await api('/api/exercises/'+currentId);
      if(S.page==='write'&&S.exercise?.id===currentId&&!S.dirty){if(d.draft.revision!==S.draftRevision){S.draftText=d.draft.value.text;S.draftRevision=d.draft.revision;if($('#translation'))$('#translation').value=S.draftText;if($('#word-count'))$('#word-count').textContent=countWords(S.draftText)+' words';}if(d.attempts.length!==S.exercise.attempts.length){S.exercise=d;if($('#feedback')&&d.attempts.length)$('#feedback').innerHTML=feedbackHtml(d.attempts[0]);}if(d.pending)watchJob(d.pending.id,'review');}
    }
  }if(S.dashboard&&S.dashboard.today!==day&&S.page==='home')await render();}catch{}
}
shell();
render();
setInterval(checkSync,7000);
