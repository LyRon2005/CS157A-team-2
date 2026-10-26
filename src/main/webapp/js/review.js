// Building review UI. All account ownership checks also run on the server.
(() => {
  const el=id=>document.getElementById(id);
  const dialog=el('review-dialog'), form=el('review-form'), text=el('review-text');
  let building=null, generation=0, offset=0, loading=false, writing=false, editing=0, entries=[];
  const token=()=>window.SJSUAuth?.getCsrfToken() || '';
  async function api(url,options={}) {
    const response=await fetch(url,{credentials:'same-origin',...options,signal:AbortSignal.timeout(15000)});
    let data; try{data=await response.json();}catch{throw new Error('The server could not return reviews. Please try again.');}
    if(!response.ok)throw new Error(data.error || 'The request could not be completed.');
    return data;
  }
  function status(message){el('review-status').textContent=message;}
  function card(review) {
    const article=document.createElement('article');article.className='review-card';
    const head=document.createElement('div');head.className='review-author';
    const avatar=document.createElement('span');avatar.className='avatar';avatar.textContent=review.author.slice(0,1).toUpperCase();avatar.setAttribute('aria-hidden','true');
    const info=document.createElement('div'),name=document.createElement('strong'),date=document.createElement('time');
    name.textContent=review.author+(review.mine?' · You':'');date.dateTime=review.created;
    date.textContent=new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',year:'numeric'}).format(new Date(review.created));
    info.append(name,date);head.append(avatar,info);const body=document.createElement('p');body.textContent=review.text;
    article.append(head,body);
    if(review.mine){const actions=document.createElement('div');actions.className='review-tools';
      for(const [label,action] of [['Edit',()=>open(review)],['Delete',()=>remove(review)]]){
        const button=document.createElement('button');button.type='button';button.textContent=label;button.onclick=action;actions.append(button);
      }article.append(actions);
    }return article;
  }
  async function load(reset=true) {
    const current=building,version=++generation;
    if(reset){offset=0;entries=[];el('building-reviews').replaceChildren();}
    el('review-more').hidden=true;el('review-retry').hidden=true;
    if(!current?.databaseId){status('Reviews are not available for this map feature yet.');return;}
    loading=true;status('Loading community reviews…');
    try{
      const data=await api(`api/reviews?buildingId=${current.databaseId}&offset=${offset}&sort=${el('review-sort').value}`);
      if(version!==generation)return;
      entries.push(...data.reviews);offset=entries.length;
      el('building-reviews').replaceChildren(...entries.map(card));
      status(entries.length?`${entries.length} review${entries.length===1?'':'s'} shown`:'A fresh perspective starts with you. Be the first to share your experience.');
      el('building-reviews').classList.toggle('empty-reviews',!entries.length);
      el('review-more').hidden=!data.hasMore;
    }catch(error){if(version===generation){status(error.name==='TimeoutError'?'Reviews took too long to load. Please retry.':error.message);el('review-retry').hidden=false;}}
    finally{if(version===generation)loading=false;}
  }
  function open(review=null) {
    if(!building?.databaseId)return;
    if(!token()){status('Sign in first. Your selected building will stay here.');el('sign-in').click();return;}
    editing=review?.id || 0;text.value=review?.text || '';
    el('review-target').textContent=building.name;el('review-message').textContent='';
    el('review-dialog-title').textContent=editing?'Refine your perspective.':'Share your perspective.';
    el('review-submit').textContent=editing?'Save changes':'Publish review ↗';
    text.dispatchEvent(new Event('input'));dialog.showModal();text.focus();
  }
  async function mutate(action,reviewId,content,current) {
    const body=new URLSearchParams({action,buildingId:current.databaseId,writtenReview:content,reviewId:String(reviewId)});
    return api('api/reviews',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Requested-With':'SJSUClassrooms','X-CSRF-Token':token()},body});
  }
  async function remove(review) {
    if(writing)return;const target=building,confirm=el('delete-dialog');confirm.returnValue='';confirm.showModal();
    confirm.addEventListener('close',async()=>{
      if(confirm.returnValue!=='delete'||writing)return;writing=true;
      try{await mutate('delete',review.id,'',target);if(building===target)await load();}
      catch(e){if(building===target)status(e.message);}finally{writing=false;}
    },{once:true});
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(writing)return;
    const value=text.value.trim();if(!value){el('review-message').textContent='Write a review before publishing.';return;}
    const target=building;writing=true;el('review-submit').disabled=true;text.readOnly=true;
    el('review-message').textContent='Saving your review…';
    try{await mutate(editing?'update':'create',editing,value,target);dialog.close();if(target===building)await load();}
    catch(e){el('review-message').textContent=e.message;}
    finally{writing=false;el('review-submit').disabled=false;text.readOnly=false;}
  });
  text.addEventListener('input',()=>{el('review-length').textContent=`${text.value.length} / 1,000`;});
  el('write-review').onclick=()=>open();el('review-close').onclick=()=>{if(!writing)dialog.close();};
  dialog.addEventListener('cancel',e=>{if(writing)e.preventDefault();});
  el('review-sort').onchange=()=>load();el('review-more').onclick=()=>{if(!loading)load(false);};el('review-retry').onclick=()=>load();
  window.addEventListener('building-selected',e=>{building={...e.detail,...window.BUILDING_DIRECTORY[e.detail.id]};el('write-review').disabled=!building.databaseId;load();});
  window.addEventListener('building-cleared',()=>{building=null;++generation;});
  window.addEventListener('auth-changed',()=>{if(building)load();});
})();
