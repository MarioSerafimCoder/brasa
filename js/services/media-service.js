const API="/api/media";let timer=null;
export const getMediaToolsStatus=()=>request("/api/media-tools/status");
export const getMediaStatus=(key)=>request(`${API}/status/${encodeURIComponent(key)}`);
export const getAllMediaStatus=()=>request(`${API}/status`);
export const getMediaQueue=()=>request(`${API}/queue`);
export const analyzeMedia=(key,prepare=false)=>request(`${API}/analyze/${encodeURIComponent(key)}`,{method:"POST",body:{prepare}});
export const prepareMedia=(key)=>request(`${API}/prepare/${encodeURIComponent(key)}`,{method:"POST"});
export const retryMedia=(key)=>request(`${API}/retry/${encodeURIComponent(key)}`,{method:"POST"});
export const cancelMedia=(key)=>request(`${API}/cancel/${encodeURIComponent(key)}`,{method:"POST"});
export const removePreparedMedia=(key)=>request(`${API}/prepared/${encodeURIComponent(key)}`,{method:"DELETE"});
export const getMediaSettings=()=>request(`${API}/settings`).then((r)=>r.settings||r);
export const saveMediaSettings=(settings)=>request(`${API}/settings`,{method:"PUT",body:settings}).then((r)=>r.settings||r);
export const pauseMediaQueue=()=>request(`${API}/queue/pause`,{method:"POST"});export const resumeMediaQueue=()=>request(`${API}/queue/resume`,{method:"POST"});
export function watchMedia(key,callback){stopWatchingMedia();const poll=async()=>{try{const result=await getMediaStatus(key);callback(result.item||result);document.dispatchEvent(new CustomEvent("brasa:media-status-change",{detail:result.item||result}));const busy=["queued","analyzing","processing","finalizing"].includes((result.item||result).status);timer=setTimeout(poll,busy?1500:6000);}catch{timer=setTimeout(poll,8000);}};poll();return stopWatchingMedia;}
export function stopWatchingMedia(){if(timer)clearTimeout(timer);timer=null;}
async function request(url,options={}){const response=await fetch(url,{method:options.method||"GET",headers:{"X-BRasa-Request":"1",...(options.body?{"Content-Type":"application/json"}:{})},body:options.body?JSON.stringify(options.body):undefined});const data=await response.json().catch(()=>({}));if(!response.ok)throw new Error(data.message||"Não foi possível acessar a preparação de mídia.");return data;}
