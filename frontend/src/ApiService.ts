export interface Member { id:number; nickname:string }
export interface Animal { id:number; species:'CAT'|'DOG'; name:string; description:string }
export interface Observation { id:number; version:number; animalId:number|null; kind:'SIGHTING'|'FEEDING'; observedAt:string; latitude:number; longitude:number; locationPrecision:'EXACT'|'APPROXIMATE'; notes:string|null; photoUrl:string|null; hasPhoto?:boolean; recorder:Member }
export interface Page<T>{items:T[];limit:number;offset:number}
export interface ObservationInput {animalId:number|null;kind:string;observedAt:string;latitude:number;longitude:number;notes:string;version?:number}
let token='';
let sessionEpoch=0;
let expired=()=>{};
export function onExpired(callback:()=>void){expired=callback}
export function clearToken(){token='';sessionEpoch++}
export class ApiError extends Error {constructor(public status:number,message:string){super(message)}}
async function request<T>(path:string,options:RequestInit={}):Promise<T>{
 const epoch=sessionEpoch;
 const headers=new Headers(options.headers);if(token)headers.set('Authorization',`Bearer ${token}`);
 if(options.body && !(options.body instanceof FormData))headers.set('Content-Type','application/json');
 const response=await fetch(`/api${path}`,{...options,headers,cache:'no-store'});
 if(epoch!==sessionEpoch)throw new ApiError(0,'登入狀態已變更，已忽略先前的回應。');
 if(!response.ok){if(response.status===401&&token){clearToken();expired()}
  const messages:Record<number,string>={400:'請確認欄位、照片格式與大小。',401:'登入已失效或帳號密碼錯誤。',403:'目前採邀請制，請聯絡測試負責人。',404:'找不到紀錄，或您無權操作。',409:'資料已被更新，請重新載入後再操作。',413:'照片超過大小限制。',429:'操作過於頻繁，請稍後重試。'};
  throw new ApiError(response.status,messages[response.status]??`服務暫時無法完成操作（${response.status}）。`)}
 if(response.status===204)return undefined as T;
 if(response.headers.get('Content-Type')?.startsWith('image/')){const blob=await response.blob();if(epoch!==sessionEpoch)throw new ApiError(0,'已清除先前的私人內容。');return blob as T;}
 const value=await response.json();if(epoch!==sessionEpoch)throw new ApiError(0,'已清除先前的私人內容。');return value as T;
}
export const api={
 async login(email:string,password:string){const result=await request<{accessToken:string;member:Member}>('/auth/login',{method:'POST',body:JSON.stringify({email,password})});token=result.accessToken;return result.member},
 async register(email:string,nickname:string,password:string,invitationCode:string){return request<Member>('/auth/register',{method:'POST',body:JSON.stringify({email,nickname,password,invitationCode})})},
 async logout(){const pending=request('/auth/logout',{method:'POST'});clearToken();await pending},
 animals(offset=0){return request<Page<Animal>>(`/animals?limit=100&offset=${offset}`)},
 createAnimal(input:{species:string;name:string;description:string}){return request<Animal>('/animals',{method:'POST',body:JSON.stringify(input)})},
 observations(offset:number,memberId?:number){return request<Page<Observation>>(memberId?`/members/${memberId}/observations?limit=10&offset=${offset}`:`/observations?limit=10&offset=${offset}`)},
 create(input:ObservationInput){return request<{id:number}>('/observations',{method:'POST',body:JSON.stringify(input)})},
 update(id:number,input:ObservationInput){return request(`/observations/${id}`,{method:'PUT',body:JSON.stringify(input)})},
 remove(id:number,version:number){return request(`/observations/${id}?version=${version}`,{method:'DELETE'})},
 upload(id:number,version:number,file:File){const form=new FormData();form.append('file',file);return request(`/observations/${id}/photo?version=${version}`,{method:'POST',body:form})},
 photo(id:number){return request<Blob>(`/observations/${id}/photo`)},
 removePhoto(id:number,version:number){return request(`/observations/${id}/photo?version=${version}`,{method:'DELETE'})}
};
