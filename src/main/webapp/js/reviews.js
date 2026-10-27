// Both review panels use the same editor. The server checks ownership and scores again.
(() => {
  const el=id=>document.getElementById(id), make=(tag,text='',cls='')=>{const n=document.createElement(tag);n.textContent=text;n.className=cls;return n;};
  const labels=window.ReviewCriteria;
  const dialog=el('review-dialog'),form=el('review-form'),text=el('review-text');
  const token=()=>window.SJSUAuth?.getCsrfToken()||'';
  let editor=null,busy=false;
  async function api(url,options={}) {
    const r=await fetch(url,{credentials:'same-origin',...options,signal:AbortSignal.timeout(15000)});
    let data;try{data=await r.json();}catch{throw Error('Reviews are unavailable. Please try again.');}
    if(!r.ok)throw Error(data.error||'Could not save your review.');return data;
  }
  function scores(container,values,kind,summary=false,counts={}) {
    container.replaceChildren();
    for(const [key,label] of Object.entries(labels[kind])) {
      const value=values?.[key];if(value==null)continue;const item=make('div','','score-item');item.append(make('span',label));
      const score=make('strong',value==null?'Not rated':summary?Number(value).toFixed(1)+' / 5':'★'.repeat(value)+'☆'.repeat(5-value));
      score.setAttribute('aria-label',label+': '+(value==null?'not rated':value+' out of 5'));item.append(score);
      if(summary&&value!=null)item.append(make('small',counts[key]+(counts[key]===1?' rating':' ratings')));
      container.append(item);
    }
  }
  function open(panel,review=null) {
    if(busy||!panel.target)return;
    if(!token()){panel.status('Sign in to share your experience.');el('sign-in').click();return;}
    editor={panel,target:panel.target,review};text.value=review?.text||'';
    el('review-target').textContent=panel.target.name;el('review-message').textContent='';
    el('review-dialog-title').textContent=review?'Edit your review':'Share your experience';
    el('review-submit').textContent=review?'Save changes':'Publish review';
    const grid=el('review-ratings');grid.replaceChildren();
    for(const [key,label] of Object.entries(labels[panel.kind])) {
      const field=make('fieldset','','star-field');field.append(make('legend',label));
      const row=make('div','','star-options');
      for(let value=0;value<=5;value++) {
        const option=make('label'),input=document.createElement('input');input.type='radio';input.name=key;input.value=value?String(value):'';
        input.checked=value===(review?.ratings?.[key]??0);input.setAttribute('aria-label',value?`${label}: ${value} star${value===1?'':'s'}`:`${label}: Not rated`);
        const mark=make('span',value?'★':'N/A');mark.setAttribute('aria-hidden','true');option.append(input,mark);row.append(option);
      }
      field.append(row);grid.append(field);
    }
    text.dispatchEvent(new Event('input'));dialog.showModal();text.focus();
  }
  async function mutate(panel,target,action,review,content,ratings={}) {
    const body=new URLSearchParams({action,[panel.kind+'Id']:target.databaseId,reviewId:String(review?.id||0),writtenReview:content,...ratings});
    return api('api/reviews',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','X-Requested-With':'SJSUClassrooms','X-CSRF-Token':token()},body});
  }
  function panel(kind,ids) {
    const p={kind,target:null,version:0,entries:[],offset:0,loading:false,status:msg=>el(ids.status).textContent=msg};
    function card(review) {
      const article=make('article','','review-card'),head=make('div','','review-author');
      const info=make('div'),name=make('strong',review.author+(review.mine?' · You':'')),date=make('time');
      date.dateTime=review.created;date.textContent=new Date(review.created).toLocaleDateString('en-US');info.append(name,date);head.append(make('span',review.author.slice(0,1).toUpperCase(),'avatar'),info);
      article.append(head,make('p',review.text));
      if(review.mine){const link=make('a','Manage in My reviews →','manage-review-link');link.href='my-reviews.html#review='+review.id;article.append(link);}
      return article;
    }
    p.load=async(reset=true)=>{
      const target=p.target,version=++p.version;
      if(reset){p.entries=[];p.offset=0;el(ids.list).replaceChildren();el(ids.summary).replaceChildren();}
      el(ids.more).hidden=true;el(ids.retry).hidden=true;
      if(!target?.databaseId){p.status('Select a room or building to see its reviews.');return;}
      p.loading=true;p.status('Loading reviews…');
      try{
        const data=await api(`api/reviews?${kind}Id=${target.databaseId}&offset=${p.offset}&sort=${el(ids.sort).value}`);
        if(version!==p.version)return;
        p.entries.push(...data.reviews);p.offset=p.entries.length;
        el(ids.list).replaceChildren(...p.entries.map(card));
        const overview=make('details','','rating-overview');overview.open=true;
        overview.append(make('summary',`Community ratings · ${data.summary.count} review${data.summary.count===1?'':'s'}`));
        const grid=make('div','','score-grid');scores(grid,data.summary.averages,kind,true,data.summary.counts);if(!grid.children.length)grid.append(make('p','No ratings yet.','empty-state'));overview.append(grid);
        el(ids.summary).replaceChildren(overview);
        p.status(data.summary.count?`${p.entries.length} of ${data.summary.count} reviews`:'No reviews yet. Be the first to share your experience.');
        el(ids.more).hidden=!data.hasMore;
      }catch(e){if(version===p.version){p.status(e.message);el(ids.retry).hidden=false;}}
      finally{if(version===p.version)p.loading=false;}
    };
    p.select=target=>{p.target=target;el(ids.write).disabled=!target?.databaseId;p.load();};
    el(ids.write).onclick=()=>open(p);el(ids.more).onclick=()=>{if(!p.loading)p.load(false);};el(ids.retry).onclick=()=>p.load();el(ids.sort).onchange=()=>p.load();
    window.addEventListener('auth-changed',()=>{if(p.target)p.load();});return p;
  }
  const building=panel('building',{list:'building-reviews',status:'review-status',summary:'building-rating-summary',more:'review-more',retry:'review-retry',sort:'review-sort',write:'write-review'});
  const classroom=panel('classroom',{list:'duncan-reviews',status:'classroom-review-status',summary:'classroom-rating-summary',more:'classroom-review-more',retry:'classroom-review-retry',sort:'classroom-review-sort',write:'classroom-write-review'});
  window.ClassroomReviews={select:room=>classroom.select(room)};
  window.addEventListener('building-selected',e=>building.select({...e.detail,...window.BUILDING_DIRECTORY[e.detail.id]}));
  window.addEventListener('building-cleared',()=>{building.select(null);classroom.select(null);});
  form.addEventListener('submit',async e=>{
    e.preventDefault();if(busy||!editor)return;const value=text.value.trim();if(!value){el('review-message').textContent='Write a review first.';return;}
    const current=editor,ratings={};for(const key of Object.keys(labels[current.panel.kind]))ratings[key]=new FormData(form).get(key)||'';
    busy=true;el('review-submit').disabled=true;text.readOnly=true;el('review-ratings').disabled=true;el('review-message').textContent='Saving…';
    try{await mutate(current.panel,current.target,current.review?'update':'create',current.review,value,ratings);dialog.close();if(current.panel.target===current.target)await current.panel.load();}
    catch(error){el('review-message').textContent=error.message;}
    finally{busy=false;el('review-submit').disabled=false;text.readOnly=false;el('review-ratings').disabled=false;}
  });
  text.addEventListener('input',()=>el('review-length').textContent=`${text.value.length} / 1,000`);
  el('review-close').onclick=()=>{if(!busy)dialog.close();};dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();});
})();
