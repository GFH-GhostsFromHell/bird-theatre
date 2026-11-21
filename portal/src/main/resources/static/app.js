/* Bird Theatre UI. All remote strings are displayed as text, never interpolated into HTML. */
'use strict';
const app = document.querySelector('#content');
const modal = document.querySelector('#modal');
const modalBody = document.querySelector('#modal-content');
const tokenKey = 'bird-theatre-session';
let token = sessionStorage.getItem(tokenKey) || '';
let profile = null;
let camerasCache = [];
let lastNotification = null;
const $ = (selector, root = document) => root.querySelector(selector);
const node = (tag, className, text) => {
  const item = document.createElement(tag);
  if (className) item.className = className;
  if (text != null) item.textContent = String(text);
  return item;
};
const niceTime = (iso) => iso ? new Date(iso).toLocaleString(undefined, {dateStyle:'medium',timeStyle:'short'}) : '—';
const safe = (fn) => async (...args) => { try { return await fn(...args); } catch (err) { toast(err.message || 'Could not complete request'); } };
const toast = (message) => {
  const box = $('#toast'); box.textContent = message; box.classList.add('visible');
  clearTimeout(toast.timeout); toast.timeout = setTimeout(() => box.classList.remove('visible'), 4400);
};
async function api(path, options = {}) {
  const headers = {'Accept': 'application/json', ...options.headers};
  if (options.body) headers['Content-Type'] = 'application/json';
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const result = await fetch(`/api/v1${path}`, {...options, headers});
  const data = await result.json().catch(() => ({}));
  if (!result.ok) {
    if (result.status === 401 && path.startsWith('/me') && path !== '/me/password') { profile = null; token = ''; sessionStorage.removeItem(tokenKey); renderMember(); }
    throw new Error(data.message || data.detail || {400:'Please check your details.',401:'Invalid credentials or expired session.',403:'Access denied.',404:'The requested item was not found.',409:'This name or email is already registered.',429:'Please wait a minute before trying again.',503:'This service is not configured yet.'}[result.status] || 'Request failed (HTTP '+result.status+').');
  }
  return data;
}
function startPage(title, subtitle, action = null) {
  app.replaceChildren();
  const head = node('section','page-intro'); const copy = node('div');
  copy.append(node('div','overline','WELCOME TO BIRD THEATRE'),node('h1','',title),node('p','',subtitle)); head.append(copy);
  if (action) head.append(action);
  app.append(head);
}
function btn(text, callback, style = 'button') {
  const button = node('button',style,text); button.type = 'button'; button.addEventListener('click',safe(callback)); return button;
}
function panel(title) { const section=node('section','panel'); section.append(node('h3','',title)); return section; }
function empty(title, subtitle, icon='🌱', action=null) {
  const section=node('section','empty'); section.append(node('div','empty-illustration',icon),node('h3','',title),node('p','',subtitle));
  if(action)section.append(action); return section;
}
function link(text,hash){const a=node('a','',text);a.href=hash;return a;}
function formField(title,name,type='text',placeholder='',value=''){
  const item=node('div','field'); const label=node('label','',title); const input=node(type==='textarea'?'textarea':'input');
  input.name=name;input.id=`field-${name}`;label.htmlFor=input.id;input.placeholder=placeholder;
  if(type!=='textarea')input.type=type; input.value=value;input.required=true;
  item.append(label,input);return item;
}
function selectField(title,name,options){
  const item=node('div','field');const label=node('label','',title);const select=node('select'); select.name=name; select.id=`field-${name}`; label.htmlFor=select.id;
  for(const [value,text] of options){const option=node('option','',text);option.value=value;select.append(option);} item.append(label,select);return item;
}
function submitForm(form,handler,label='Save'){
  const actions=node('div','form-actions'); const submit=node('button','button secondary',label);submit.type='submit';actions.append(submit);form.append(actions);
  form.addEventListener('submit',safe(async(event)=>{event.preventDefault();submit.disabled=true;try{const input=Object.fromEntries(new FormData(form)); await handler(input);}finally{submit.disabled=false;}}));
}
function requiresLogin(){if(profile)return true; openAuth('login'); toast('Sign in to use your bird station tools.'); return false;}
function renderMember(){
  const slot=$('#member-actions');slot.replaceChildren();
  if(profile){
    const pill=node('div','user-pill');
    pill.append(node('span','user-avatar',profile.displayName[0]?.toUpperCase()||'B'),
      node('span','',profile.displayName),
      btn('My profile',()=>{location.hash='#profile';},'small-button'),
      btn('Sign out',async()=>{
        try{await api('/auth/logout',{method:'POST'});}catch(e){}
        token='';profile=null;sessionStorage.removeItem(tokenKey);
        renderMember();location.hash='#explore';route();
      },'text-button'));
    slot.append(pill);
  }else{
    slot.append(btn('Sign in',()=>openAuth('login'),'text-button'));
    slot.append(btn('Create account',()=>openAuth('signup'),'small-button'));
  }
}
function openDialog(content){modalBody.replaceChildren();modalBody.append(content);modal.showModal();}
function closeDialog(){modal.close();}
$('#close-modal').onclick=closeDialog;
modal.addEventListener('click',event=>{if(event.target===modal)closeDialog();});
function openAuth(mode='login'){
  const titles={login:'Welcome back',signup:'Join Bird Theatre',verify:'Verify your email',
    recover:'Reset your password',reset:'Set a new password',username:'Find your username'};
  const root=node('div','account-dialog');
  root.append(node('h2','',titles[mode]||'Account access'));
  root.append(node('p','','One account for Bird Theatre on Windows, Android, iPhone and the web.'));
  const tabs=node('div','auth-tabs');
  for(const [name,label] of [['login','Sign in'],['signup','Create'],['verify','Verify'],['recover','Recover']]){
    tabs.append(btn(label,()=>openAuth(name),name===mode?'current':''));
  }
  root.append(tabs);
  const form=node('form','form-grid');form.setAttribute('autocomplete','on');
  const add=(label,key,type='text',placeholder='',required=true,value='')=>{
    const field=formField(label,key,type,placeholder,value);
    field.querySelector('input').required=required; form.append(field);return field;
  };
  if(mode==='signup'){
    add('Username','handle','text','3–32 letters, numbers or underscores');
    add('Display name','displayName','text','Your community name');
    add('Email address','email','email','you@example.com');
    add('Password','password','password','At least 12 characters');
    add('Confirm password','confirmPassword','password','Re-enter password');
    root.append(node('p','helper','A six-digit code will be sent by email to activate your new account.'));
    submitForm(form,async input=>{
      if(input.password!==input.confirmPassword)throw Error('Passwords do not match.');
      delete input.confirmPassword;
      const response=await api('/auth/register/start',{method:'POST',body:JSON.stringify(input)});
      closeDialog();
      openAuth('verify');
      const handle=$('#field-handle',modalBody);if(handle)handle.value=response.handle;
      toast(response.message||'Check your inbox for a verification code.');
    },'Send verification email');
  }else if(mode==='verify'){
    add('Username','handle','text','Your account username');
    add('Six-digit verification code','code','text','123456');
    submitForm(form,async input=>{
      const response=await api('/auth/register/verify',{method:'POST',body:JSON.stringify(input)});
      token=response.token;sessionStorage.setItem(tokenKey,token);
      profile=await api('/me/profile');
      closeDialog();renderMember();toast('Your account is verified and ready.');
      location.hash='#profile';route();
    },'Verify and sign in');
  }else if(mode==='recover'){
    add('Username or email','account','text','Username or verified email');
    submitForm(form,async input=>{
      const response=await api('/auth/recovery/start',{method:'POST',body:JSON.stringify(input)});
      closeDialog();openAuth('reset');toast(response.message);
    },'Email me a reset code');
    root.append(btn('Forgot username?',()=>openAuth('username'),'text-button'));
  }else if(mode==='reset'){
    add('Username','handle','text','Your account username');
    add('Six-digit reset code','code','text','123456');
    add('New password','newPassword','password','At least 12 characters');
    submitForm(form,async input=>{
      await api('/auth/recovery/complete',{method:'POST',body:JSON.stringify(input)});
      toast('Password changed. Sign in with your new password.');openAuth('login');
    },'Reset password');
  }else if(mode==='username'){
    add('Email address','email','email','Your verified email');
    submitForm(form,async input=>{
      const response=await api('/auth/recovery/username',{method:'POST',body:JSON.stringify(input)});
      toast(response.message);openAuth('login');
    },'Email my username');
  }else{
    add('Username','handle','text','Your Bird Theatre username');
    add('Password','password','password','Account password');
    submitForm(form,async input=>{
      const response=await api('/auth/login',{method:'POST',body:JSON.stringify(input)});
      token=response.token;sessionStorage.setItem(tokenKey,token);
      profile=await api('/me/profile');
      closeDialog();renderMember();toast('Welcome back, '+profile.displayName+'!');
      location.hash='#profile';route();
    },'Sign in');
    root.append(btn('Forgot password?',()=>openAuth('recover'),'text-button'));
  }
  root.append(form);openDialog(root);
  // Show an actionable reason before someone attempts to register without Mailjet.
  if(['signup','recover','username'].includes(mode)){
    api('/auth/options').then(options=>{
      if(options.emailConfigured || !modalBody.contains(root))return;
      const info=node('p','notice warning',
        'Email verification is being configured. Registration and password recovery '+
        'will be available once the Bird Theatre Mailjet connection is enabled.');
      root.insertBefore(info,form);
      const submit=form.querySelector('button[type="submit"]');
      if(submit)submit.disabled=true;
    }).catch(()=>{/* Account API errors are handled on submission. */});
  }
}

async function memberProfile(){
  if(!profile){
    startPage('Your Bird Theatre account','Sign in or register to manage your profile, bird stations, notifications and saved activity.');
    const login=btn('Sign in',()=>openAuth('login'),'button secondary');
    const signup=btn('Create an account',()=>openAuth('signup'),'button ghost');
    const box=panel('Join the community');box.append(node('p','',
      'Your Bird Theatre profile works across Windows, Android, iPhone and the web.'));
    const row=node('div','button-row');row.append(login,signup);box.append(row);app.append(box);return;
  }
  const me=await api('/me/profile');
  profile=me;renderMember();
  startPage('My profile','Manage the details associated with your Bird Theatre account and what other members can see.');
  const columns=node('div','two-cols');
  const identity=panel('Community profile');
  const form=node('form','form-grid');
  const displayName=formField('Display name','displayName','text','How members see you',me.displayName);
  const region=formField('General region','region','text','e.g. Ontario, Canada',me.region||'');
  region.querySelector('input').required=false;
  const bio=formField('About me','bio','textarea','Favourite birds, feeders or wildlife interests',me.bio||'');
  bio.querySelector('textarea').required=false;
  bio.classList.add('full');
  form.append(displayName,region,bio);
  const switchRow=node('label','profile-checkbox');
  const privacy=node('input');privacy.type='checkbox';privacy.checked=!!me.publicProfile;
  switchRow.append(privacy,node('span','',
      'Public member profile (your email is never displayed)'));
  form.append(switchRow);
  submitForm(form,async input=>{
    const saved=await api('/me/profile',{method:'PUT',body:JSON.stringify({
      displayName:input.displayName,region:input.region||'',
      bio:input.bio||'',publicProfile:privacy.checked
    })});
    profile=saved;renderMember();toast('Your profile is updated.');
  },'Save profile changes');
  identity.append(form);
  const account=panel('Account & privacy');
  account.append(node('p','profile-label','USERNAME'),node('strong','',me.handle),
    node('p','profile-label','EMAIL'),node('strong','',me.email||'Legacy account — email not linked'),
    node('p','helper',me.emailVerified?'Email verified':'Email verification not available for this account'),
    node('p','profile-label','MEMBER SINCE'),node('strong','',niceTime(me.joinedAt)));
  if(me.publicProfile)account.append(link('View public profile','#member/'+me.handle));
  account.append(node('div','notice','Your email address and sign-in credentials are never displayed on public member profiles.'));
  columns.append(identity,account);app.append(columns);
  const security=panel('Change password');
  const pw=node('form','form-grid');
  pw.append(formField('Current password','currentPassword','password','Current password'));
  pw.append(formField('New password','newPassword','password','At least 12 characters'));
  submitForm(pw,async input=>{
    const response=await api('/me/password',{method:'POST',body:JSON.stringify(input)});
    token=response.token;sessionStorage.setItem(tokenKey,token);
    toast('Password changed; other sessions were signed out.');pw.reset();
  },'Update password');
  security.append(pw);security.append(node('p','helper','Changing your password signs out other sessions. Password recovery requires a verified email.'));
  app.append(security);
}

async function publicMember(handle){
  const me=await api('/members/'+encodeURIComponent(handle));
  startPage(me.displayName,me.region||'Bird Theatre community member');
  const card=panel('About '+me.displayName);
  card.append(node('p','',me.bio||'This member has not added a biography yet.'));
  card.append(node('p','helper','Member since '+niceTime(me.joinedAt)));
  app.append(card);
}
function cameraCard(camera,sample=false){
  const item=node('article','camera-card');const art=node('div','card-art'+(camera.kind==='HOUSE'?' house':''));
  art.append(node('span','emoji',camera.kind==='HOUSE'?'🪺':'🐦'));
  art.append(node('span','chip'+(camera.online?' live':''),sample?'DESIGN PREVIEW':(camera.online?'● DEVICE REPORTING':'◯ STREAM LISTED')));
  const details=node('div','card-content');const topline=node('div','card-topline');
  topline.append(node('div','card-title',camera.name),node('span','pill',camera.kind==='HOUSE'?'Nest box':'Feeder'));
  details.append(topline,node('p','card-desc',camera.description||'A place to watch local wildlife, one visit at a time.'));
  const foot=node('div','card-footer');foot.append(node('span','',`⌖ ${camera.region}`));
  if(!sample)foot.append(link('Watch station →',`#camera/${camera.id}`));
  else foot.append(node('span','','Illustration only'));
  details.append(foot);item.append(art,details);return item;
}
async function explore(){
  app.innerHTML=`<section class="hero"><div class="hero-copy"><div class="overline">✦ EVERY VISITOR HAS A STORY</div><h1>A front-row seat to <em>the wild.</em></h1><p>Watch backyard bird feeders and nesting boxes, discover who visits, and share those small wonderful moments with bird lovers everywhere.</p><div class="button-row"><a class="button" href="#stations">Explore live stations&nbsp; ↗</a><a class="button ghost" href="#cameras">Share my camera</a></div></div><div class="hero-garden" aria-hidden="true"><span class="hero-bird">🐦</span><span class="flower">🌼</span></div></section><section class="stats-strip"><div class="stat-tile"><div><strong id="stat-cams">—</strong><small>Community cameras</small></div><span class="stat-icon">📹</span></div><div class="stat-tile"><div><strong id="stat-homes">—</strong><small>Nest & feeder stations</small></div><span class="stat-icon">🪺</span></div><div class="stat-tile"><div><strong>24/7</strong><small>Nature on its own schedule</small></div><span class="stat-icon">🌿</span></div></section><section id="stations"><div class="section-heading"><div><div class="overline">DISCOVER THE COMMUNITY</div><h2>Bird stations to explore</h2><p>Browse shared cameras from backyards near and far.</p></div></div><div class="filterbar"><input class="search" id="camera-search" type="search" placeholder="Search by station or region" aria-label="Search stations"></div><div id="camera-list" class="camera-grid"></div></section>`;
  const all=await api('/cameras');camerasCache=all;
  $('#stat-cams').textContent=all.length;$('#stat-homes').textContent=all.length;
  function display(needle=''){
    const container=$('#camera-list');container.replaceChildren();
    const found=all.filter(c=>`${c.name} ${c.region} ${c.description}`.toLowerCase().includes(needle.toLowerCase()));
    if(found.length)found.forEach(c=>container.append(cameraCard(c)));
    else if(needle){container.append(empty('No matching cameras','Try a different station or region.','🔎'));}
    else {
      const message=empty('The first stories are just beginning','No public stations are connected yet. Add yours to make Bird Theatre come alive.','🪺',btn('Connect a bird camera',()=>{location.hash='#cameras'},'button secondary'));
      message.style.gridColumn='1 / -1'; container.append(message);
      const examples=[{name:'Backyard Feeder',kind:'FEEDER',region:'Sample station',description:'See feeding visits, species predictions and daily patterns.'},{name:'The Nest Box',kind:'HOUSE',region:'Sample station',description:'A quiet window into the lives of nesting birds.'},{name:'Woodland Watch',kind:'FEEDER',region:'Sample station',description:'Get notified when a new feathered guest arrives.'}];
      examples.forEach(c=>container.append(cameraCard(c,true)));
    }
  }
  $('#camera-search').addEventListener('input',e=>display(e.target.value));display();
}
function newCameraForm(){
  const root=node('div');root.append(node('h2','','Connect a bird station'),node('p','','Add a publicly reachable HTTPS HLS (.m3u8) stream from your own camera or streaming gateway.'));
  const form=node('form','form-grid');form.append(formField('Station name','name','text','e.g. Willow Creek Feeder'));
  form.append(selectField('Camera type','kind',[['FEEDER','Bird feeder'],['HOUSE','Nest box / bird house']]));
  form.append(formField('General region (not your address)','region','text','e.g. Georgian Bay, Ontario'));
  form.append(formField('HLS playback URL','streamUrl','url','https://example.com/live/feeder.m3u8'));
  form.append(formField('About this station','description','textarea','What makes this spot special?'));
  const check=node('div','field full'); const checkLabel=node('label');const checkbox=node('input');checkbox.type='checkbox';checkbox.name='publicListing';checkbox.checked=true;
  checkLabel.append(checkbox,document.createTextNode(' Show this station in the public community feed'));check.append(checkLabel);form.append(check);
  const note=node('div','notice');note.textContent='For your safety, share only a broad region, point cameras away from people and neighbouring homes, and use a stream that you have permission to publish.';form.append(note);
  submitForm(form,async(input)=>{
    input.publicListing=Boolean(input.publicListing);
    const created=await api('/cameras',{method:'POST',body:JSON.stringify(input)});
    closeDialog();showDeviceSecret(created.deviceToken,created.camera);
  },'Connect my station');root.append(form);openDialog(root);
}
function showDeviceSecret(secret,camera){
  const root=node('div');root.append(node('h2','','Camera connected!'),node('p','',`Your station “${camera.name}” is saved. This private device token is needed for recognition and RFID ingestion.`));
  root.append(node('div','notice warning','Copy this key now. It is shown only once. Anyone who has it can submit bird sightings for this station.'));
  root.append(node('div','secret',secret));
  const actions=node('div','form-actions');actions.append(btn('Copy device token',async()=>{await navigator.clipboard.writeText(secret);toast('Token copied. Keep it private.');}),btn('Go to my stations',()=>{closeDialog();location.hash='#cameras';route();},'button ghost'));
  root.append(actions);openDialog(root);
}
async function myCameras(){
  startPage('My bird stations','Manage your feeders and nest boxes. Connect a stream, then attach an AI or RFID reader using its private device token.',btn('+ Add bird station',()=>{if(requiresLogin())newCameraForm();}));
  if(!requiresLogin()){
    app.append(empty('Sign in to manage stations','Your camera settings and private RFID records live in your member account.','🌿',btn('Sign in',()=>openAuth('login'))));return;
  }
  const cams=await api('/me/cameras');
  if(!cams.length){app.append(empty('No cameras connected yet','Start by adding your bird feeder or nesting box stream.','🪹',btn('Connect first camera',newCameraForm,'button secondary')));return;}
  const grid=node('div','camera-grid');cams.forEach(c=>grid.append(cameraCard(c)));app.append(grid);
  const hint=panel('Connect species recognition or an RFID reader');hint.append(node('p','','Use your camera ID and the one-time device token with the edge-agent scripts. You can rotate a lost token from this screen. Never share it in a public post.'));
  for(const c of cams){const line=node('div','list-row');line.append(node('div','list-icon',c.kind==='HOUSE'?'🪺':'🐦'));
    const text=node('div');text.append(node('strong','',c.name),node('small','',`Camera ID: ${c.id}`));line.append(text);
    line.append(btn('Rotate device token',async()=>{if(!confirm(`Invalidate the existing device token for ${c.name}? Your edge camera will need the new key.`))return;
      const result=await api(`/cameras/${c.id}/rotate-device-token`,{method:'POST'});showDeviceSecret(result.deviceToken,c);},'small-button'));hint.append(line);
  }app.append(hint);
}
function makeTable(headers, rows){
  const wrap=node('div','table-wrap');const table=node('table','data-table');const thead=node('thead');const hr=node('tr');headers.forEach(h=>hr.append(node('th','',h)));thead.append(hr);table.append(thead);
  const tbody=node('tbody');rows.forEach(row=>{const tr=node('tr');row.forEach(value=>tr.append(node('td','',value)));tbody.append(tr);});table.append(tbody);wrap.append(table);return wrap;
}
async function sightings(){
  startPage('Sightings & statistics','Discover which species have visited, how often they return, and when they arrive or depart. Counts are estimated events, not unique individual birds.');
  const all=await api('/cameras'); const owned=profile?await api('/me/cameras'):[];
  const cams=[...owned,...all.filter(c=>!owned.some(o=>o.id===c.id))];
  if(!cams.length){app.append(empty('Waiting for the first sighting','Once a bird camera is connected and sending events, its visitor history will appear here.','🐦'));return;}
  const control=panel('Choose a bird station');const selector=node('select','search');selector.setAttribute('aria-label','Choose a camera');
  cams.forEach(c=>{const opt=node('option','',`${c.name} · ${c.region}`);opt.value=c.id;selector.append(opt);});
  control.append(selector);app.append(control);
  const results=node('div');app.append(results);
  const update=safe(async()=>{
    const id=selector.value;const [stats,history]=await Promise.all([api(`/cameras/${id}/statistics`),api(`/cameras/${id}/sightings`)]);
    results.replaceChildren();const tiles=node('div','stats-strip');
    for(const [label,value,icon] of [['Estimated visits',stats.totalEstimatedVisits,'🐦'],['Observed species',stats.speciesCount,'🌿'],['RFID reads',stats.rfidReads,'⌁']]){
      const tile=node('div','stat-tile');const body=node('div');body.append(node('strong','',value),node('small','',label));tile.append(body,node('span','stat-icon',icon));tiles.append(tile);
    }results.append(tiles);
    const columns=node('div','two-cols');columns.style.marginTop='19px';const species=panel('Most frequent visitors');const chart=node('div','bar-chart');
    const counts=Object.entries(stats.bySpecies).sort((a,b)=>b[1]-a[1]);
    if(!counts.length)chart.append(node('p','muted','No bird species have been identified yet.'));
    const max=Math.max(1,...counts.map(x=>x[1]));
    counts.forEach(([name,count])=>{const row=node('div','bar-row');row.append(node('span','',name));const track=node('div','bar-track');const fill=node('div','bar-fill');fill.style.width=`${Math.max(5,count/max*100)}%`;track.append(fill);row.append(track,node('strong','',count));chart.append(row);});
    species.append(chart);const recent=panel('Recent events');
    if(history.length)recent.append(makeTable(['Bird','Event','Source','When'],history.slice(0,30).map(s=>[s.species,s.eventType.toLowerCase(),s.source,niceTime(s.observedAt)])));
    else recent.append(node('p','muted','No visits recorded yet.'));columns.append(species,recent);results.append(columns);
    const days=panel('Daily activity · last 7 days');days.append(node('p','muted',`${stats.totalEstimatedVisits} estimated arrivals and ${stats.departures} departure signals in the past 30 days.`));
    const dayChart=node('div','bar-chart');const allDays=[];
    for(let i=6;i>=0;i--){const date=new Date();date.setUTCDate(date.getUTCDate()-i);allDays.push(date.toISOString().slice(0,10));}
    const maxDay=Math.max(1,...allDays.map(day=>stats.byDay[day]||0));
    allDays.forEach(day=>{const count=stats.byDay[day]||0;const row=node('div','bar-row');row.append(node('span','',day.slice(5)));
      const track=node('div','bar-track');const fill=node('div','bar-fill');fill.style.width=`${count/maxDay*100}%`;track.append(fill);row.append(track,node('strong','',count));dayChart.append(row);});
    days.append(dayChart);results.append(days);
  });selector.addEventListener('change',update);await update();
}
async function alerts(){
  startPage('Bird alerts','Know when a feathered visitor arrives. Bird Theatre records alerts in your inbox and can notify this open browser tab.');
  if(!requiresLogin()){app.append(empty('Alerts follow your stations','Sign in to set species-specific notifications for your bird cameras.','🔔'));return;}
  const owned=await api('/me/cameras');const publicCams=await api('/cameras');const available=[...owned,...publicCams.filter(c=>!owned.some(o=>o.id===c.id))];const columns=node('div','two-cols');const create=panel('Create an alert rule');
  if(available.length){const form=node('form','form-grid');const camera=selectField('Bird station','cameraId',available.map(c=>[c.id,c.name]));camera.classList.add('full');form.append(camera);
    form.append(formField('Species or ANY','species','text','ANY','ANY'));
    const period=selectField('Alert cooldown','cooldownMinutes',[['5','5 minutes'],['15','15 minutes'],['30','30 minutes'],['60','1 hour']]);form.append(period);
    submitForm(form,async(input)=>{const id=input.cameraId;delete input.cameraId;input.cooldownMinutes=Number(input.cooldownMinutes);
      await api(`/cameras/${id}/alert-rules`,{method:'POST',body:JSON.stringify(input)});toast('Your bird alert is active.');route();},'Set alert');create.append(form);
  }else create.append(node('p','','Join or connect a publicly shared camera first to configure alerts.'));
  create.append(node('div','notice','Browser notifications work while this app is open and notifications are permitted. Background/mobile push delivery is a future integration.'));
  create.append(btn('Enable browser notifications',async()=>{if(!('Notification' in window)){toast('This browser does not support notifications.');return;} const status=await Notification.requestPermission();toast(`Notification permission: ${status}`);},'button ghost'));
  const inbox=panel('Recent bird alerts');const [rules,notices]=await Promise.all([api('/me/alert-rules'),api('/me/notifications')]);
  inbox.append(node('p','',`${rules.length} alert rules • ${notices.length} recent inbox messages`));const list=node('div','compact-list');
  if(notices.length)notices.forEach(n=>{const row=node('div','list-row');row.append(node('div','list-icon','🔔'));
    const text=node('div');text.append(node('strong','',n.message),node('small','',niceTime(n.createdAt)));row.append(text);list.append(row);});
  else list.append(node('p','muted','Your alert inbox is empty. Visits will appear here after your edge reader submits an arrival.'));
  inbox.append(list);columns.append(create,inbox);app.append(columns);
}
async function rfid(){
  startPage('RFID bird monitoring','Associate supported RFID identifiers with individual birds and see registered-tag arrival and departure events at your own stations.');
  if(!requiresLogin()){app.append(empty('An identity for every tagged visitor','Log in to register RFID identifiers you are legally authorized to monitor.','⌁'));return;}
  const columns=node('div','two-cols');const create=panel('Register an existing RFID tag');
  create.append(node('div','notice warning','Never tag or handle wild birds without any necessary authorizations and specialist oversight. Bird Theatre does not provide physical tagging tools.'));
  const form=node('form','form-grid');form.append(formField('Tag UID','tagUid','text','Reader-provided tag code'),formField('Nickname','nickname','text','e.g. Ruby'),formField('Species','species','text','e.g. Northern Cardinal'));
  submitForm(form,async(input)=>{await api('/me/tags',{method:'POST',body:JSON.stringify(input)});toast('RFID tag registered.');route();},'Register tag');create.append(form);
  const lookup=panel('My registered birds');const tags=await api('/me/tags');
  if(tags.length)lookup.append(makeTable(['Bird','Species','Tag UID'],tags.map(t=>[t.nickname,t.species,t.tagUid])));
  else lookup.append(node('p','muted','No tagged birds registered yet. An RFID-compatible reader and a separate edge gateway are required for scanning.'));
  columns.append(create,lookup);app.append(columns);
  const activity=panel('Recent tagged-bird visits');
  const events=await api('/me/rfid-events');
  if(events.length){
    activity.append(makeTable(['Bird','Station','Movement','When'],events.slice(0,50).map(e=>[`${e.nickname} · ${e.species}`,e.camera,e.eventType.toLowerCase(),niceTime(e.observedAt)])));
  } else activity.append(node('p','muted','No RFID reader events received yet. Your station will show tagged arrivals and departures here.'));
  app.append(activity);
  const info=panel('How RFID tracking works'); info.append(node('p','','A compatible RFID reader detects a previously fitted tag. A local gateway sends the tag identifier and an arrival/departure event to your Bird Theatre station using its private device token. Matching registered tags are associated with a species; unknown tags are recorded as unidentified.'));
  app.append(info);
}
async function cameraDetails(id){
  const camera=await api(`/cameras/${id}`);startPage(camera.name,`${camera.kind==='HOUSE'?'Nest box':'Feeder'} · ${camera.region} · Shared by ${camera.owner}`,btn('Back to cameras',()=>{location.hash='#explore'},'button ghost'));
  const layout=node('div','two-cols');const left=node('div');const player=node('div','player');const video=node('video');video.controls=true;video.autoplay=true;video.muted=true;video.playsInline=true;video.preload='none';
  const placeholder=node('div','player-placeholder');placeholder.append(node('span','big',camera.kind==='HOUSE'?'🪺':'🐦'),node('strong','','Stream player'),node('p','','Video becomes available when the owner connects an accessible HLS broadcast.'));
  player.append(placeholder,video);left.append(player);const description=panel('About this station');description.append(node('p','',camera.description||'A place to watch nature.'));left.append(description);
  const right=panel('Visitor activity');const latest=await api(`/cameras/${id}/sightings`);const stats=await api(`/cameras/${id}/statistics`);
  right.append(node('div','pill',`${stats.totalEstimatedVisits} estimated visits · last 30 days`));
  if(!latest.length)right.append(node('p','muted','No sightings yet. Come back after this camera starts reporting bird activity.'));
  else latest.slice(0,15).forEach(s=>{const line=node('div','list-row');line.append(node('div','list-icon','🐦'));
    const words=node('div');words.append(node('strong','',s.species),node('small','',`${s.eventType.toLowerCase()} · ${niceTime(s.observedAt)}`));line.append(words);right.append(line);});
  layout.append(left,right);app.append(layout);
  const url=new URL(camera.streamUrl); if(url.protocol==='https:' && url.pathname.endsWith('.m3u8')){
    video.style.display='block'; video.onerror=()=>toast('Stream is unavailable or blocked by the stream server.');
    if(video.canPlayType('application/vnd.apple.mpegurl')) video.src=url.href;
    else if(window.Hls?.isSupported()) {const hls=new Hls({enableWorker:true});hls.loadSource(url.href);hls.attachMedia(video);video.addEventListener('loadedmetadata',()=>video.play().catch(()=>{}));}
    else {video.style.display='none';placeholder.append(node('p','','This browser cannot play HLS without native support.'));}
  }
}
async function pollNotices(){
  if(!token || document.hidden)return;
  try{const incoming=await api('/me/notifications');const top=incoming[0];
    if(top && lastNotification && top.id!==lastNotification && 'Notification' in window && Notification.permission==='granted'){
      new Notification('Bird Theatre • New visitor',{body:top.message});}
    if(top)lastNotification=top.id;
  }catch(e) { /* quiet background polling; user actions show errors */ }
}
async function route(){
  const value=(location.hash||'#explore').substring(1); const [page,id]=value.split('/');
  document.querySelectorAll('[data-route]').forEach(item=>item.classList.toggle('active',item.dataset.route===page || (page==='camera' && item.dataset.route==='explore')));
  window.scrollTo({top:0,behavior:'instant'});
  try {
    if(page==='profile'||page==='account')await memberProfile();
    else if(page==='member' && id)await publicMember(id);
    else if(page==='register'){await memberProfile();openAuth('signup');}
    else if(page==='signin'){await memberProfile();openAuth('login');}
    else if(page==='cameras')await myCameras();else if(page==='sightings')await sightings();
    else if(page==='alerts')await alerts();else if(page==='rfid')await rfid();
    else if(page==='camera' && id)await cameraDetails(id);else await explore();
  } catch(err){app.replaceChildren(empty('Something interrupted the birds',err.message||'Please try again.','🌦',btn('Retry',route)));}
}
window.addEventListener('hashchange',route);
(async()=>{if(token){try{profile=await api('/me');}catch(e){token='';sessionStorage.removeItem(tokenKey);}}
 renderMember(); await route(); await pollNotices();setInterval(pollNotices,30000);})();
