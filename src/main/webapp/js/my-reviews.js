// Personal review history and a separate editing screen. Ownership is checked by MySQL queries.
(() => {
  const el=id=>document.getElementById(id),make=(tag,text='',className='')=>{const n=document.createElement(tag);n.textContent=text;n.className=className;return n;};
  let csrf='',user='',rows=[],current=null,busy=false,dirty=false,version=0,lastHash='',loading=false;
  const status=message=>el('personal-status').textContent=message;
  function clearSession() {
    csrf='';user='';current=null;rows=[];dirty=false;++version;
    el('personal-list').replaceChildren();el('personal-text').value='';el('personal-ratings').replaceChildren();
    el('personal-list-view').hidden=true;el('personal-edit-view').hidden=true;el('personal-gate').hidden=false;
    status('Sign in to manage your reviews.');
    if(el('personal-delete-dialog').open)el('personal-delete-dialog').close();
  }
  async function api(url,options={}) {
    const response=await fetch(url,{credentials:'same-origin',...options,signal:AbortSignal.timeout(15000)});
    let data;try{data=await response.json();}catch{throw Error('The server could not return your reviews. Try again.');}
    if(response.status===401)clearSession();
    if(!response.ok)throw Error(data.error||'The request could not be completed.');return data;
  }
  function card(review) {
    const article=make('article','','personal-card'),top=make('div','','personal-card-top');
    const time=make('time',new Date(review.created).toLocaleDateString('en-US'));time.dateTime=review.created;
    top.append(make('span',review.kind==='building'?'BUILDING':'CLASSROOM','review-badge'),time);
    article.append(top,make('h2',review.targetName),make('p',review.text));
    const bottom=make('div','','personal-card-bottom'),rated=Object.values(review.ratings).filter(v=>v!==null).length;
    bottom.append(make('span',`${rated} of ${Object.keys(review.ratings).length} criteria rated`));
    const button=make('button',review.editable?'Manage review →':'View review →');button.type='button';button.onclick=()=>location.hash='review='+review.id;bottom.append(button);article.append(bottom);return article;
  }
  async function load(reset=true) {
    if(!user)return;const request=++version;loading=true;
    el('personal-gate').hidden=true;el('personal-list-view').hidden=false;el('personal-edit-view').hidden=true;
    if(reset){rows=[];el('personal-list').replaceChildren();el('personal-count').textContent='';}
    el('personal-more').hidden=true;el('personal-retry').hidden=true;status('Loading your reviews…');
    try {
      const query=new URLSearchParams({mine:'1',type:el('personal-type').value,sort:el('personal-sort').value,offset:String(rows.length)});
      const data=await api('api/reviews?'+query);if(request!==version||!user)return;
      rows.push(...data.reviews);el('personal-list').replaceChildren(...rows.map(card));
      el('personal-count').textContent=`${rows.length} review${rows.length===1?'':'s'} shown`;
      status(rows.length?'Select a review to view its scores or make changes.':'No reviews here yet. Explore campus and share your first experience.');
      el('personal-more').hidden=!data.hasMore;
    } catch(error){if(request===version&&user){status(error.message);el('personal-retry').hidden=false;}}
    finally{if(request===version)loading=false;}
  }
  async function edit(id) {
    if(!user)return;const request=++version;current=null;dirty=false;
    el('personal-list-view').hidden=true;el('personal-edit-view').hidden=true;status('Loading review…');
    try {
      const data=await api('api/reviews?mine=1&reviewId='+id);if(request!==version||!user)return;
      current=data.reviews[0];el('personal-edit-view').hidden=false;status('');
      el('personal-kind').textContent=current.kind.toUpperCase()+' REVIEW';el('personal-edit-title').textContent=current.targetName;
      el('personal-date').textContent='Published '+new Date(current.created).toLocaleDateString('en-US');
      el('personal-text').value=current.text;el('personal-length').textContent=`${current.text.length} / 1,000`;el('personal-edit-message').textContent=current.editable?'':'Editing rooms outside Duncan Hall is not enabled yet.';
      el('personal-edit-fields').disabled=!current.editable;
      const grid=el('personal-ratings');grid.replaceChildren();
      for(const [key,label] of Object.entries(window.ReviewCriteria[current.kind])) {
        const field=make('fieldset','','star-field');field.append(make('legend',label));const options=make('div','','star-options');
        for(let value=0;value<=5;value++) {
          const option=make('label'),input=document.createElement('input');input.type='radio';input.name=key;input.value=value?String(value):'';input.checked=value===(current.ratings[key]??0);
          input.setAttribute('aria-label',value?`${label}: ${value} star${value===1?'':'s'}`:`${label}: Not rated`);
          const symbol=make('span',value?'★':'N/A');symbol.setAttribute('aria-hidden','true');option.append(input,symbol);options.append(option);
        }field.append(options);grid.append(field);
      }
      el('personal-edit-title').focus();
    } catch(error){if(request===version&&user){status(error.message);el('personal-list-view').hidden=false;el('personal-retry').hidden=false;}}
  }
  function route() {
    const next=location.hash;
    if(busy||(dirty&&!confirm('Discard your unsaved changes?'))) {history.replaceState(null,'',location.pathname+location.search+lastHash);return;}
    dirty=false;lastHash=next;
    const match=/^#review=([1-9][0-9]{0,8})$/.exec(next);
    if(match)edit(match[1]);else{current=null;load();}
  }
  async function session() {
    try {
      const response=await api('api/auth/me');
      if(!response.user){clearSession();return;}
      const changed=user!==response.user.studentId;csrf=response.csrfToken;user=response.user.studentId;
      el('personal-gate').hidden=true;if(changed){dirty=false;route();}
    }catch(error){clearSession();status(error.message);}
  }
  async function mutate(action) {
    if(busy||!current||!current.editable)return;
    const review=current;const text=el('personal-text').value.trim();
    if(action==='update'&&!text){el('personal-edit-message').textContent='Write a review before saving.';return;}
    const body=new URLSearchParams({action,reviewId:String(review.id),[review.kind+'Id']:String(review.targetId),writtenReview:text});
    const values=new FormData(el('personal-edit-form'));
    for(const key of Object.keys(window.ReviewCriteria[review.kind]))body.set(key,values.get(key)||'');
    busy=true;el('personal-edit-fields').disabled=true;el('personal-back').disabled=true;el('personal-edit-message').textContent=action==='delete'?'Deleting…':'Saving…';
    try {
      await api('api/reviews',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Requested-With':'SJSUClassrooms','X-CSRF-Token':csrf},body});
      dirty=false;current=null;busy=false;history.replaceState(null,'',location.pathname+'#reviews');lastHash='#reviews';
      await load();if(user)status(action==='delete'?'Review deleted.':'Changes saved. Your review is up to date.');
    }catch(error){if(user)el('personal-edit-message').textContent=error.message;}
    finally{busy=false;el('personal-back').disabled=false;el('personal-edit-fields').disabled=!user||!current?.editable;}
  }
  el('personal-edit-form').addEventListener('input',()=>{dirty=true;el('personal-length').textContent=`${el('personal-text').value.length} / 1,000`;});
  el('personal-edit-form').addEventListener('submit',e=>{e.preventDefault();mutate('update');});
  const back=()=>{if(!busy)location.hash='reviews';};el('personal-back').onclick=back;el('personal-cancel').onclick=back;
  el('personal-delete').onclick=()=>{if(busy||!current)return;const dialog=el('personal-delete-dialog');dialog.returnValue='';dialog.showModal();};
  el('personal-delete-dialog').addEventListener('close',()=>{if(el('personal-delete-dialog').returnValue==='delete')mutate('delete');});
  el('personal-type').onchange=()=>load();el('personal-sort').onchange=()=>load();el('personal-more').onclick=()=>{if(!loading)load(false);};el('personal-retry').onclick=()=>route();
  el('session-retry').onclick=session;
  window.addEventListener('hashchange',route);window.addEventListener('focus',session);
  window.addEventListener('pageshow',event=>{if(event.persisted)session();});
  window.addEventListener('beforeunload',event=>{if(dirty||busy){event.preventDefault();event.returnValue='';}});
  session();
})();
