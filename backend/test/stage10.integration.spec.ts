import {afterAll,beforeAll,describe,expect,it} from 'vitest';
import {randomUUID} from 'crypto';
import {Pool} from 'pg';
import {WebSocket,type RawData} from 'ws';
import * as jwt from 'jsonwebtoken';

const base=process.env.API_URL??'http://localhost:8080';
const pool=new Pool({connectionString:process.env.DATABASE_URL});
type Account={id:string;email:string;accessToken:string;refreshToken:string};
const accounts:Account[]=[];

async function raw(path:string,init:RequestInit={},token?:string){
  const headers:Record<string,string>={...(token?{authorization:`Bearer ${token}`}:{})};
  if(init.body&&!(init.body instanceof FormData))headers['content-type']='application/json';
  const response=await fetch(base+path,{...init,headers:{...headers,...(init.headers??{})}});
  const body=await response.json().catch(()=>({}));
  return{response,body};
}
async function api(path:string,init:RequestInit={},token?:string){const result=await raw(path,init,token);if(!result.response.ok)throw new Error(`${result.response.status} ${JSON.stringify(result.body)}`);return result.body}
async function account(prefix:string):Promise<Account>{const email=`stage10-${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}@test.velora`;const session=await api('/auth/register',{method:'POST',body:JSON.stringify({email,password:'SecurePass123!'})});const me=await api('/users/me',{},session.accessToken);const value={email,id:me.id,...session};accounts.push(value);return value}
async function onboard(user:Account,name:string,gender:string,bio:string){await api('/users/me/onboarding',{method:'PUT',body:JSON.stringify({name,birthDate:'1994-04-12',gender,city:'Kazan',bio,datingGoal:'relationship',interests:['Travel','Coffee','Photography'],showGenders:['woman','man','non_binary','other']})},user.accessToken);await api('/users/me/location',{method:'PUT',body:JSON.stringify({country:'Russia',region:'Tatarstan',city:'Kazan',latitude:55.796,longitude:49.109,accuracyMeters:100,locationSource:'gps'})},user.accessToken)}
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
async function upload(user:Account,position:number){const form=new FormData();form.append('file',new Blob([png],{type:'image/png'}),`profile-${position}.png`);return api(`/users/me/photos/${position}/upload`,{method:'POST',body:form},user.accessToken)}
async function connect(user:Account,conversationId:string,header=false){return new Promise<WebSocket>((resolve,reject)=>{const socket=new WebSocket(`${base.replace('http','ws')}/ws/chat?conversationId=${conversationId}${header?'':`&token=${encodeURIComponent(user.accessToken)}`}`,header?{headers:{authorization:`Bearer ${user.accessToken}`}}:undefined);const timer=setTimeout(()=>reject(new Error('WebSocket connection timeout')),5_000);socket.on('message',data=>{if(JSON.parse(data.toString()).type==='connected'){clearTimeout(timer);resolve(socket)}});socket.once('error',reject)})}
async function socketEvent(socket:WebSocket,predicate:(event:any)=>boolean){return new Promise<any>((resolve,reject)=>{const timer=setTimeout(()=>{socket.off('message',listener);reject(new Error('WebSocket event timeout'))},5_000);const listener=(data:RawData)=>{const event=JSON.parse(data.toString());if(predicate(event)){clearTimeout(timer);socket.off('message',listener);resolve(event)}};socket.on('message',listener)})}
async function socketClosed(socket:WebSocket){return new Promise<{code:number;reason:string}>((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('WebSocket close timeout')),5_000);socket.once('close',(code,reason)=>{clearTimeout(timer);resolve({code,reason:reason.toString()})})})}

describe.sequential('Stage 10 closed beta flows',()=>{
  let a:Account,b:Account,c:Account;
  let matchId='',conversationId='',firstMessageId='';

  beforeAll(async()=>{
    [a,b,c]=await Promise.all([account('a'),account('b'),account('c')]);
    await Promise.all([
      onboard(a,'Artem','man','A complete long biography that must remain visible without single-line truncation.'),
      onboard(b,'Bianca','woman','Public biography with travel, coffee, photography and enough detail for the full profile screen.'),
      onboard(c,'Clara','woman','Pagination candidate profile.'),
    ]);
  });

  it('returns a cursor-paged discover feed without private coordinates',async()=>{
    const first=await api('/feed/discover?limit=1',{},a.accessToken);
    expect(first.items).toHaveLength(1);expect(first.nextCursor).toBeTruthy();
    const second=await api(`/feed/discover?limit=1&cursor=${encodeURIComponent(first.nextCursor)}`,{},a.accessToken);
    expect(second.items[0].user_id).not.toBe(first.items[0].user_id);
    expect(JSON.stringify([first,second])).not.toMatch(/latitude|longitude|accuracy_m|location_source|email|phone/);
  });

  it('processes every profile-photo variant and supports main/reorder/delete',async()=>{
    const one=await upload(b,0),two=await upload(b,1);
    const gapForm=new FormData();gapForm.append('file',new Blob([png],{type:'image/png'}),'gap.png');const gap=await raw('/users/me/photos/4/upload',{method:'POST',body:gapForm},b.accessToken);expect(gap.response.status).toBe(400);expect(gap.body.code).toBe('PHOTO_POSITION_INVALID');
    const own=await api('/users/me/photos',{},b.accessToken);expect(own.map((item:any)=>item.id)).toEqual([one.id,two.id]);
    const reordered=await api('/users/me/photos/order',{method:'PUT',body:JSON.stringify({photoIds:[two.id,one.id]})},b.accessToken);expect(reordered[0].id).toBe(two.id);
    const main=await api(`/users/me/photos/${one.id}/main`,{method:'PUT'},b.accessToken);expect(main[0].id).toBe(one.id);
    for(const key of ['thumbnailUrl','mediumUrl','fullUrl']){const media=await fetch(base+one[key],{headers:{authorization:`Bearer ${a.accessToken}`}});expect(media.status).toBe(200);expect(media.headers.get('content-type')).toBe('image/jpeg');expect((await media.arrayBuffer()).byteLength).toBeGreaterThan(0)}
    await api(`/users/me/photos/${two.id}`,{method:'DELETE'},b.accessToken);expect((await api('/users/me/photos',{},b.accessToken)).map((item:any)=>item.position)).toEqual([0]);
  });

  it('serves a complete safe public profile and aggregates relationship state',async()=>{
    const profile=await api(`/users/${b.id}/profile`,{},a.accessToken);
    expect(profile).toMatchObject({userId:b.id,displayName:'Bianca',relationshipState:'NONE',canMessage:false,city:'Kazan'});
    expect(profile.bio).toContain('enough detail');expect(profile.interests).toEqual(['Coffee','Photography','Travel']);expect(profile.photos).toHaveLength(1);
    expect(JSON.stringify(profile)).not.toMatch(/email|phone|latitude|longitude|oauth|subject|refresh|moderation|device|address/i);
    await api('/subscription/development/activate-premium',{method:'POST'},a.accessToken);
    await api('/notifications/devices',{method:'POST',body:JSON.stringify({token:`development-stage10-${a.id}`,platform:'development'})},a.accessToken);
    await api(`/likes/${a.id}`,{method:'POST',body:JSON.stringify({kind:'like'})},b.accessToken);
    expect((await api(`/users/${b.id}/profile`,{},a.accessToken)).relationshipState).toBe('LIKED_ME');
    const likes=await api('/feed/likes?limit=1',{},a.accessToken);expect(likes.locked).toBe(false);expect(likes.items[0].user_id).toBe(b.id);
    const matched=await api(`/likes/${b.id}`,{method:'POST',body:JSON.stringify({kind:'like'})},a.accessToken);
    expect(matched.matched).toBe(true);matchId=matched.matchId;conversationId=matched.conversationId;
    expect((await api(`/users/${b.id}/profile`,{},a.accessToken)).relationshipState).toBe('MATCHED');
    const delivery=await pool.query("SELECT status FROM notification_delivery_attempts nda JOIN notifications n ON n.id=nda.notification_id WHERE n.user_id=$1 AND n.type='like' ORDER BY nda.attempted_at DESC LIMIT 1",[a.id]);expect(delivery.rows[0].status).toBe('sent');
  });

  it('deduplicates messages, pages history, reconnects WebSocket, and honors read-receipt privacy',async()=>{
    const socket=await connect(a,conversationId);socket.terminate();
    const reconnected=await connect(a,conversationId,true);expect(reconnected.readyState).toBe(WebSocket.OPEN);
    const clientMessageId=randomUUID();
    const sent=await api(`/conversations/${conversationId}/messages`,{method:'POST',body:JSON.stringify({body:'Exactly once',clientMessageId})},a.accessToken);firstMessageId=sent.id;expect(sent.delivered_at).toBeNull();
    const duplicate=await api(`/conversations/${conversationId}/messages`,{method:'POST',body:JSON.stringify({body:'Exactly once',clientMessageId})},a.accessToken);expect(duplicate.id).toBe(sent.id);expect(duplicate.duplicate).toBe(true);
    expect(Number((await pool.query('SELECT count(*) count FROM messages WHERE sender_id=$1 AND client_message_id=$2',[a.id,clientMessageId])).rows[0].count)).toBe(1);
    for(let index=0;index<3;index++)await api(`/conversations/${conversationId}/messages`,{method:'POST',body:JSON.stringify({body:`Page ${index}`,clientMessageId:randomUUID()})},a.accessToken);
    const deliveredEvent=socketEvent(reconnected,event=>event.type==='delivered'&&event.messageIds?.includes(firstMessageId));const first=await api(`/feed/conversations/${conversationId}/messages?limit=2`,{},b.accessToken);expect(first.items).toHaveLength(2);expect(first.nextCursor).toBeTruthy();expect((await deliveredEvent).messageIds).toContain(firstMessageId);expect((await pool.query('SELECT delivered_at FROM messages WHERE id=$1',[firstMessageId])).rows[0].delivered_at).not.toBeNull();
    const second=await api(`/feed/conversations/${conversationId}/messages?limit=2&cursor=${encodeURIComponent(first.nextCursor)}`,{},b.accessToken);expect(new Set([...first.items,...second.items].map((item:any)=>item.id)).size).toBe(first.items.length+second.items.length);
    await api('/privacy',{method:'PATCH',body:JSON.stringify({readReceipts:false,pushNotifications:false})},b.accessToken);
    const read=await api(`/conversations/${conversationId}/read`,{method:'PATCH',body:'{}'},b.accessToken);expect(read.receiptsShared).toBe(false);
    expect((await pool.query('SELECT read_at FROM messages WHERE id=$1',[firstMessageId])).rows[0].read_at).toBeNull();
    const pages=await Promise.all([api('/feed/matches?limit=1',{},a.accessToken),api('/feed/conversations?limit=1',{},a.accessToken)]);expect(pages[0].items[0].user_id).toBe(b.id);expect(pages[1].items[0].other_user_id).toBe(b.id);reconnected.close();
  });

  it('refreshes a session after an expired access token',async()=>{
    const expired=jwt.sign({sub:a.id,email:a.email},process.env.JWT_ACCESS_SECRET!,{expiresIn:-1});
    expect((await raw('/users/me',{},expired)).response.status).toBe(401);
    const rotated=await api('/auth/refresh',{method:'POST',body:JSON.stringify({refreshToken:a.refreshToken})});
    a.accessToken=rotated.accessToken;a.refreshToken=rotated.refreshToken;
    expect((await api('/users/me',{},a.accessToken)).id).toBe(a.id);
  });

  it('enforces privacy server-side while retaining matched profile access',async()=>{
    const observer=await connect(a,conversationId);const onlineEvent=socketEvent(observer,event=>event.type==='online'&&event.userId===b.id&&event.active===true);const subject=await connect(b,conversationId);await onlineEvent;
    expect((await pool.query('SELECT presence_active FROM profiles WHERE user_id=$1',[b.id])).rows[0].presence_active).toBe(true);
    const hiddenEvent=socketEvent(observer,event=>event.type==='online'&&event.userId===b.id&&event.active===false);
    const privacy=await api('/privacy',{method:'PATCH',body:JSON.stringify({showOnlineStatus:false,showDistance:false,showCity:false,discoverableProfile:false,hideProfileTemporarily:true})},b.accessToken);
    await hiddenEvent;const subjectClose=socketClosed(subject);subject.close();await subjectClose;observer.close();
    for(let attempt=0;attempt<20;attempt++){if(!(await pool.query('SELECT presence_active FROM profiles WHERE user_id=$1',[b.id])).rows[0].presence_active)break;await new Promise(resolve=>setTimeout(resolve,10))}
    expect((await pool.query('SELECT presence_active FROM profiles WHERE user_id=$1',[b.id])).rows[0].presence_active).toBe(false);
    expect(privacy).toMatchObject({showOnlineStatus:false,showDistance:false,showCity:false,discoverableProfile:false,hideProfileTemporarily:true});
    const matchedProfile=await api(`/users/${b.id}/profile`,{},a.accessToken);expect(matchedProfile.city).toBeNull();expect(matchedProfile.distanceKm).toBeNull();expect(matchedProfile.onlineStatus).toBe('HIDDEN');
    const stranger=await raw(`/users/${b.id}/profile`,{},c.accessToken);expect(stranger.response.status).toBe(404);expect((await api('/feed/discover?limit=40',{},c.accessToken)).items.some((item:any)=>item.user_id===b.id)).toBe(false);
    await api(`/conversations/${conversationId}/messages`,{method:'POST',body:JSON.stringify({body:'Push preference check',clientMessageId:randomUUID()})},a.accessToken);
    const disabled=await pool.query("SELECT status,error_code FROM notification_delivery_attempts nda JOIN notifications n ON n.id=nda.notification_id WHERE n.user_id=$1 AND n.type='message' ORDER BY nda.attempted_at DESC LIMIT 1",[b.id]);expect(disabled.rows[0]).toMatchObject({status:'disabled',error_code:'PUSH_DISABLED'});
  });

  it('retains chat history but prevents writes after unmatch, report, and block',async()=>{
    const aSocket=await connect(a,conversationId),bSocket=await connect(b,conversationId),closed=socketClosed(bSocket);
    await api(`/matches/${matchId}`,{method:'DELETE'},a.accessToken);
    expect((await closed).code).toBe(1008);aSocket.close();
    expect((await raw(`/conversations/${conversationId}/messages`,{method:'POST',body:JSON.stringify({body:'must fail',clientMessageId:randomUUID()})},a.accessToken)).response.status).toBe(404);
    expect(Number((await pool.query('SELECT count(*) count FROM messages WHERE conversation_id=$1',[conversationId])).rows[0].count)).toBeGreaterThan(0);
    expect((await api('/feed/matches',{},a.accessToken)).items.some((item:any)=>item.id===matchId)).toBe(false);
    await api('/reports',{method:'POST',body:JSON.stringify({userId:b.id,reason:'harassment',details:'Stage 10 safety scenario'})},a.accessToken);
    const report=await pool.query('SELECT reason,details,moderation_status,retained_until FROM reports WHERE reporter_id=$1 AND reported_id=$2 ORDER BY created_at DESC LIMIT 1',[a.id,b.id]);expect(report.rows[0]).toMatchObject({reason:'harassment',details:'Stage 10 safety scenario',moderation_status:'pending'});expect(report.rows[0].retained_until).not.toBeNull();
    await api('/blocks',{method:'POST',body:JSON.stringify({userId:b.id})},a.accessToken);expect((await raw(`/users/${b.id}/profile`,{},a.accessToken)).response.status).toBe(404);expect((await raw(`/likes/${b.id}`,{method:'POST',body:JSON.stringify({kind:'like'})},a.accessToken)).response.status).toBe(404);expect(Number((await pool.query('SELECT count(*) count FROM likes WHERE (from_user_id=$1 AND to_user_id=$2) OR (from_user_id=$2 AND to_user_id=$1)',[a.id,b.id])).rows[0].count)).toBe(0);
  });

  it('soft-deletes with purge metadata and revokes every session',async()=>{
    const deleted=await account('delete');await onboard(deleted,'Deleted beta','other','Soft delete retention test.');
    const result=await api('/users/me',{method:'DELETE'},deleted.accessToken);expect(result.ok).toBe(true);expect(Date.parse(result.purgeAfter)).toBeGreaterThan(Date.now());
    expect((await raw('/auth/login',{method:'POST',body:JSON.stringify({email:deleted.email,password:'SecurePass123!'})})).response.status).toBe(401);
    expect((await raw('/auth/refresh',{method:'POST',body:JSON.stringify({refreshToken:deleted.refreshToken})})).response.status).toBe(401);
    const row=(await pool.query('SELECT active,deleted_at,purge_after FROM users WHERE id=$1',[deleted.id])).rows[0];expect(row.active).toBe(false);expect(row.deleted_at).not.toBeNull();expect(row.purge_after).not.toBeNull();
  });

  afterAll(async()=>{for(const user of accounts)await api('/users/me',{method:'DELETE'},user.accessToken).catch(()=>{});await pool.end()});
});
