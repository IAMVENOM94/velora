import {Body,Controller,Get,Headers,HttpException,Param,Patch,Query,UseGuards} from '@nestjs/common';
import {ApiBearerAuth,ApiTags} from '@nestjs/swagger';
import {ActiveUserGuard,AuthService,Db} from './platform';
import {RealtimeHub} from './realtime';
import {isUuid} from './security';

type Cursor={at?:string;id:string};
type RelationshipState='NONE'|'LIKED'|'LIKED_ME'|'MATCHED';

const requireUuid=(value:unknown,label='id')=>{const id=String(value??'');if(!isUuid(id))throw new HttpException({code:'VALIDATION_ERROR',message:`Invalid ${label}`},400);return id};
const limitOf=(value:unknown,max=50)=>{const limit=Number(value??20);if(!Number.isInteger(limit)||limit<1||limit>max)throw new HttpException({code:'VALIDATION_ERROR',message:`Limit must be between 1 and ${max}`},400);return limit};
const encodeCursor=(cursor:Cursor)=>Buffer.from(JSON.stringify(cursor),'utf8').toString('base64url');
const decodeCursor=(value:unknown,withTime=false):Cursor|null=>{
  if(value===undefined||value===null||value==='')return null;
  try{
    const parsed=JSON.parse(Buffer.from(String(value),'base64url').toString('utf8')) as Cursor;
    if(!isUuid(parsed.id)||withTime&&(!parsed.at||Number.isNaN(Date.parse(parsed.at))))throw 0;
    return parsed;
  }catch{throw new HttpException({code:'INVALID_CURSOR',message:'Pagination cursor is invalid'},400)}
};

export function presenceOf(onlineAt:unknown,visible:boolean,active=false,now=Date.now()){
  if(!visible)return{onlineStatus:'HIDDEN' as const,lastSeen:null};
  if(active)return{onlineStatus:'ONLINE' as const,lastSeen:null};
  if(!onlineAt)return{onlineStatus:'OFFLINE' as const,lastSeen:null};
  const date=new Date(String(onlineAt));
  const elapsed=now-date.getTime();
  if(elapsed<=30*60_000)return{onlineStatus:'RECENTLY_ONLINE' as const,lastSeen:null};
  return{onlineStatus:'LAST_SEEN' as const,lastSeen:date.toISOString()};
}

const publicPhoto=(row:any)=>({
  id:row.id,
  position:Number(row.position),
  thumbnailUrl:`/media/${row.id}/thumbnail`,
  mediumUrl:`/media/${row.id}/medium`,
  fullUrl:`/media/${row.id}/full`,
  width:Number(row.width),
  height:Number(row.height),
});

@UseGuards(ActiveUserGuard)
@ApiBearerAuth()
@ApiTags('public-profile')
@Controller('users')
export class PublicProfileController{
  constructor(private db:Db,private auth:AuthService){}

  @Get(':userId/profile')
  async profile(@Headers('authorization') authorization:string,@Param('userId') rawUserId:string){
    const viewerId=this.auth.user(authorization).sub;
    const userId=requireUuid(rawUserId,'user id');
    if(userId===viewerId)throw new HttpException({code:'OWN_PROFILE',message:'Use the current-user profile endpoint'},409);
    const row=(await this.db.query(`
      SELECT p.user_id,p.name,extract(year from age(p.birth_date))::int age,p.birth_date,p.gender,p.bio,p.dating_goal,
              p.online_at,p.presence_active,p.hidden,p.verified,privacy.show_online_status,privacy.show_distance,privacy.show_city,
             privacy.discoverable,CASE WHEN privacy.show_city THEN COALESCE(location.city,p.city) END city,
             CASE WHEN privacy.show_distance AND me_location.latitude IS NOT NULL AND location.latitude IS NOT NULL THEN
               CASE WHEN 6371*2*asin(sqrt(power(sin(radians(location.latitude-me_location.latitude)/2),2)+cos(radians(me_location.latitude))*cos(radians(location.latitude))*power(sin(radians(location.longitude-me_location.longitude)/2),2)))<1 THEN 0
               ELSE round(6371*2*asin(sqrt(power(sin(radians(location.latitude-me_location.latitude)/2),2)+cos(radians(me_location.latitude))*cos(radians(location.latitude))*power(sin(radians(location.longitude-me_location.longitude)/2),2)))::numeric)::int END
             END distance_km
      FROM profiles p
      JOIN users target ON target.id=p.user_id AND target.active=true AND target.deleted_at IS NULL AND target.suspended_at IS NULL
      JOIN preferences privacy ON privacy.user_id=p.user_id
      LEFT JOIN profile_locations location ON location.user_id=p.user_id
      LEFT JOIN profile_locations me_location ON me_location.user_id=$1
      WHERE p.user_id=$2 AND p.onboarding_complete=true
        AND NOT EXISTS(SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=$2) OR (blocker_id=$2 AND blocked_id=$1))
    `,[viewerId,userId])).rows[0];
    if(!row)throw new HttpException({code:'PROFILE_UNAVAILABLE',message:'Profile is unavailable'},404);

    const relationship=(await this.db.query(`
      SELECT m.id match_id,c.id conversation_id,
        EXISTS(SELECT 1 FROM likes WHERE from_user_id=$1 AND to_user_id=$2) liked,
        EXISTS(SELECT 1 FROM likes WHERE from_user_id=$2 AND to_user_id=$1) liked_me
      FROM (SELECT 1) seed
      LEFT JOIN matches m ON m.unmatched_at IS NULL AND ((m.user_a_id=$1 AND m.user_b_id=$2) OR (m.user_a_id=$2 AND m.user_b_id=$1))
      LEFT JOIN conversations c ON c.match_id=m.id
    `,[viewerId,userId])).rows[0];
    const relationshipState:RelationshipState=relationship.match_id?'MATCHED':relationship.liked?'LIKED':relationship.liked_me?'LIKED_ME':'NONE';
    if((row.hidden||!row.discoverable)&&relationshipState!=='MATCHED')throw new HttpException({code:'PROFILE_UNAVAILABLE',message:'Profile is unavailable'},404);

    const [photos,interests,socialProfiles]=await Promise.all([
      this.db.query("SELECT id,position,width,height FROM photos WHERE user_id=$1 AND processing_status='ready' ORDER BY position",[userId]),
      this.db.query('SELECT i.name FROM user_interests ui JOIN interests i ON i.id=ui.interest_id WHERE ui.user_id=$1 ORDER BY i.name',[userId]),
      this.db.query('SELECT provider,username,profile_url FROM social_profiles WHERE user_id=$1 AND visible_in_profile=true ORDER BY provider',[userId]),
    ]);
    await this.db.query('INSERT INTO profile_views(viewer_id,viewed_id) VALUES($1,$2)',[viewerId,userId]).catch(()=>{});
    return{
      userId:row.user_id,
      displayName:row.name,
      age:Number(row.age),
      gender:row.gender,
      bio:row.bio,
      relationshipGoal:row.dating_goal,
      city:row.city??null,
      distanceKm:row.distance_km===null?null:Number(row.distance_km),
      verified:Boolean(row.verified),
      ...presenceOf(row.online_at,Boolean(row.show_online_status),Boolean(row.presence_active)),
      relationshipState,
      matchId:relationship.match_id??null,
      conversationId:relationship.conversation_id??null,
      canMessage:relationshipState==='MATCHED',
      photos:photos.rows.map(publicPhoto),
      interests:interests.rows.map(value=>value.name),
      socialProfiles:socialProfiles.rows.map(value=>({provider:value.provider,username:value.username,profileUrl:value.profile_url})),
    };
  }
}

@UseGuards(ActiveUserGuard)
@ApiBearerAuth()
@ApiTags('privacy')
@Controller('privacy')
export class PrivacyController{
  constructor(private db:Db,private auth:AuthService,private realtime:RealtimeHub){}
  private userId(value:string){return this.auth.user(value).sub}
  private map(row:any){return{
    showOnlineStatus:Boolean(row.show_online_status),showDistance:Boolean(row.show_distance),showCity:Boolean(row.show_city),
    discoverableProfile:Boolean(row.discoverable),readReceipts:Boolean(row.read_receipts),pushNotifications:Boolean(row.notifications),
    hideProfileTemporarily:Boolean(row.hidden),
  }}
  @Get() async get(@Headers('authorization') authorization:string){const row=(await this.db.query('SELECT pref.show_online_status,pref.show_distance,pref.show_city,pref.discoverable,pref.read_receipts,pref.notifications,p.hidden FROM preferences pref LEFT JOIN profiles p ON p.user_id=pref.user_id WHERE pref.user_id=$1',[this.userId(authorization)])).rows[0];return this.map(row)}
  @Patch() async update(@Headers('authorization') authorization:string,@Body() body:any){
    const fields=['showOnlineStatus','showDistance','showCity','discoverableProfile','readReceipts','pushNotifications','hideProfileTemporarily'];
    for(const field of fields)if(body[field]!==undefined&&typeof body[field]!=='boolean')throw new HttpException({code:'VALIDATION_ERROR',message:`${field} must be boolean`,fieldErrors:{[field]:'boolean required'}},400);
    const id=this.userId(authorization);const client=await this.db.pool.connect();
    try{await client.query('BEGIN');await client.query(`UPDATE preferences SET show_online_status=COALESCE($2,show_online_status),show_distance=COALESCE($3,show_distance),show_city=COALESCE($4,show_city),discoverable=COALESCE($5,discoverable),read_receipts=COALESCE($6,read_receipts),notifications=COALESCE($7,notifications) WHERE user_id=$1`,[id,body.showOnlineStatus,body.showDistance,body.showCity,body.discoverableProfile,body.readReceipts,body.pushNotifications]);if(body.hideProfileTemporarily!==undefined){const profile=await client.query('UPDATE profiles SET hidden=$2 WHERE user_id=$1 RETURNING user_id',[id,body.hideProfileTemporarily]);if(!profile.rowCount)throw new HttpException({code:'PROFILE_REQUIRED',message:'Complete profile first'},409)}await client.query('COMMIT')}catch(error){await client.query('ROLLBACK');throw error}finally{client.release()}
    if(body.showOnlineStatus!==undefined)this.realtime.setPresenceVisibility(id,body.showOnlineStatus);
    return this.get(authorization);
  }
}

@UseGuards(ActiveUserGuard)
@ApiBearerAuth()
@ApiTags('pagination')
@Controller('feed')
export class PaginatedFeedController{
  constructor(private db:Db,private auth:AuthService,private realtime:RealtimeHub){}
  private userId(value:string){return this.auth.user(value).sub}
  private page(rows:any[],limit:number,cursor:(row:any)=>Cursor){const hasMore=rows.length>limit;const items=rows.slice(0,limit);return{items,nextCursor:hasMore&&items.length?encodeCursor(cursor(items[items.length-1])):null}}

  @Get('discover') async discover(@Headers('authorization') authorization:string,@Query('cursor') rawCursor?:string,@Query('limit') rawLimit?:string){
    const id=this.userId(authorization),limit=limitOf(rawLimit,40),cursor=decodeCursor(rawCursor);const distance=`6371*2*asin(sqrt(power(sin(radians(candidate_location.latitude-me_location.latitude)/2),2)+cos(radians(me_location.latitude))*cos(radians(candidate_location.latitude))*power(sin(radians(candidate_location.longitude-me_location.longitude)/2),2)))`;
    const rows=(await this.db.query(`SELECT p.user_id,p.name,extract(year from age(p.birth_date))::int age,p.bio,p.dating_goal,p.gender,p.verified,CASE WHEN candidate_pref.show_city THEN COALESCE(candidate_location.city,p.city) END city,CASE WHEN candidate_pref.show_distance AND me_location.latitude IS NOT NULL AND candidate_location.latitude IS NOT NULL THEN CASE WHEN ${distance}<1 THEN 0 ELSE round((${distance})::numeric)::int END END distance_km,p.online_at,p.presence_active,candidate_pref.show_online_status,ARRAY(SELECT i.name FROM user_interests ui JOIN interests i ON i.id=ui.interest_id WHERE ui.user_id=p.user_id ORDER BY i.name) interests,ARRAY(SELECT '/media/'||ph.id::text||'/medium' FROM photos ph WHERE ph.user_id=p.user_id AND ph.processing_status='ready' ORDER BY ph.position) photos FROM profiles p JOIN users candidate ON candidate.id=p.user_id AND candidate.active=true AND candidate.deleted_at IS NULL AND candidate.suspended_at IS NULL JOIN preferences candidate_pref ON candidate_pref.user_id=p.user_id AND candidate_pref.discoverable=true JOIN preferences my_pref ON my_pref.user_id=$1 LEFT JOIN profile_locations me_location ON me_location.user_id=$1 LEFT JOIN profile_locations candidate_location ON candidate_location.user_id=p.user_id WHERE p.user_id<>$1 AND p.hidden=false AND p.onboarding_complete=true AND ($2::uuid IS NULL OR p.user_id>$2) AND extract(year from age(p.birth_date)) BETWEEN my_pref.min_age AND my_pref.max_age AND p.gender=ANY(my_pref.show_genders) AND (me_location.latitude IS NULL OR candidate_location.latitude IS NULL OR ${distance}<=my_pref.max_distance_km) AND NOT EXISTS(SELECT 1 FROM likes WHERE from_user_id=$1 AND to_user_id=p.user_id) AND NOT EXISTS(SELECT 1 FROM discover_actions da WHERE da.user_id=$1 AND da.target_user_id=p.user_id AND da.kind='pass' AND da.created_at>now()-interval '7 days') AND NOT EXISTS(SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=p.user_id) OR (blocker_id=p.user_id AND blocked_id=$1)) ORDER BY p.user_id LIMIT $3`,[id,cursor?.id??null,limit+1])).rows;
    return this.page(rows.map(row=>({...row,photo:row.photos[0]??null,...presenceOf(row.online_at,Boolean(row.show_online_status),Boolean(row.presence_active))})),limit,row=>({id:row.user_id}));
  }

  @Get('matches') async matches(@Headers('authorization') authorization:string,@Query('cursor') rawCursor?:string,@Query('limit') rawLimit?:string){
    const id=this.userId(authorization),limit=limitOf(rawLimit),cursor=decodeCursor(rawCursor,true);
    const rows=(await this.db.query(`SELECT m.id,m.created_at,c.id conversation_id,p.user_id,p.name,p.online_at,p.presence_active,pref.show_online_status,(SELECT '/media/'||ph.id::text||'/thumbnail' FROM photos ph WHERE ph.user_id=p.user_id AND ph.processing_status='ready' ORDER BY position LIMIT 1) photo FROM matches m JOIN profiles p ON p.user_id=CASE WHEN m.user_a_id=$1 THEN m.user_b_id ELSE m.user_a_id END JOIN users other_user ON other_user.id=p.user_id AND other_user.active=true AND other_user.deleted_at IS NULL AND other_user.suspended_at IS NULL JOIN preferences pref ON pref.user_id=p.user_id LEFT JOIN conversations c ON c.match_id=m.id WHERE (m.user_a_id=$1 OR m.user_b_id=$1) AND m.unmatched_at IS NULL AND ($2::timestamptz IS NULL OR (m.created_at,m.id)<($2,$3::uuid)) AND NOT EXISTS(SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=p.user_id) OR (blocker_id=p.user_id AND blocked_id=$1)) ORDER BY m.created_at DESC,m.id DESC LIMIT $4`,[id,cursor?.at??null,cursor?.id??null,limit+1])).rows;
    return this.page(rows.map(row=>({...row,...presenceOf(row.online_at,Boolean(row.show_online_status),Boolean(row.presence_active))})),limit,row=>({at:new Date(row.created_at).toISOString(),id:row.id}));
  }

  @Get('conversations') async conversations(@Headers('authorization') authorization:string,@Query('cursor') rawCursor?:string,@Query('limit') rawLimit?:string){
    const id=this.userId(authorization),limit=limitOf(rawLimit),cursor=decodeCursor(rawCursor,true);
    const rows=(await this.db.query(`SELECT c.id,c.match_id,other.user_id other_user_id,p.name,p.online_at,p.presence_active,pref.show_online_status,(SELECT '/media/'||ph.id::text||'/thumbnail' FROM photos ph WHERE ph.user_id=p.user_id AND ph.processing_status='ready' ORDER BY position LIMIT 1) photo,last_message.body last_message,last_message.kind last_message_kind,COALESCE(last_message.created_at,c.created_at) sort_at,last_message.created_at last_message_at,(SELECT count(*)::int FROM messages incoming WHERE incoming.conversation_id=c.id AND incoming.sender_id<>$1 AND (cm.last_read_at IS NULL OR incoming.created_at>cm.last_read_at)) unread_count FROM conversation_members cm JOIN conversations c ON c.id=cm.conversation_id JOIN matches m ON m.id=c.match_id AND m.unmatched_at IS NULL JOIN conversation_members other ON other.conversation_id=c.id AND other.user_id<>$1 JOIN profiles p ON p.user_id=other.user_id JOIN users other_user ON other_user.id=p.user_id AND other_user.active=true AND other_user.deleted_at IS NULL AND other_user.suspended_at IS NULL JOIN preferences pref ON pref.user_id=p.user_id LEFT JOIN LATERAL (SELECT body,kind,created_at FROM messages WHERE conversation_id=c.id ORDER BY created_at DESC,id DESC LIMIT 1) last_message ON true WHERE cm.user_id=$1 AND ($2::timestamptz IS NULL OR (COALESCE(last_message.created_at,c.created_at),c.id)<($2,$3::uuid)) AND NOT EXISTS(SELECT 1 FROM blocks WHERE (blocker_id=$1 AND blocked_id=p.user_id) OR (blocker_id=p.user_id AND blocked_id=$1)) ORDER BY COALESCE(last_message.created_at,c.created_at) DESC,c.id DESC LIMIT $4`,[id,cursor?.at??null,cursor?.id??null,limit+1])).rows;
    return this.page(rows.map(row=>({...row,last_message:row.last_message??(row.last_message_kind==='image'?'Photo':null),...presenceOf(row.online_at,Boolean(row.show_online_status),Boolean(row.presence_active))})),limit,row=>({at:new Date(row.sort_at).toISOString(),id:row.id}));
  }

  @Get('conversations/:conversationId/messages') async messages(@Headers('authorization') authorization:string,@Param('conversationId') rawConversationId:string,@Query('cursor') rawCursor?:string,@Query('limit') rawLimit?:string){
    const id=this.userId(authorization),conversationId=requireUuid(rawConversationId,'conversation id'),limit=limitOf(rawLimit,100),cursor=decodeCursor(rawCursor,true);
    const member=await this.db.query('SELECT 1 FROM conversation_members cm JOIN conversations c ON c.id=cm.conversation_id JOIN matches m ON m.id=c.match_id AND m.unmatched_at IS NULL WHERE cm.conversation_id=$1 AND cm.user_id=$2 AND NOT EXISTS(SELECT 1 FROM blocks b JOIN conversation_members peer ON peer.conversation_id=cm.conversation_id AND peer.user_id<>$2 WHERE (b.blocker_id=$2 AND b.blocked_id=peer.user_id) OR (b.blocker_id=peer.user_id AND b.blocked_id=$2))',[conversationId,id]);
    if(!member.rowCount)throw new HttpException({code:'CONVERSATION_UNAVAILABLE',message:'Conversation is unavailable'},404);
    const delivered=await this.db.query('UPDATE messages SET delivered_at=now() WHERE conversation_id=$1 AND sender_id<>$2 AND delivered_at IS NULL RETURNING id',[conversationId,id]);
    if(delivered.rowCount)this.realtime.broadcast(conversationId,{type:'delivered',userId:id,messageIds:delivered.rows.map(row=>row.id)});
    const rows=(await this.db.query(`SELECT id,conversation_id,sender_id,kind,body,reply_to_id,created_at,delivered_at,read_at,client_message_id,CASE WHEN media_key IS NULL THEN NULL ELSE '/media/messages/'||id::text END media_url FROM messages WHERE conversation_id=$1 AND ($2::timestamptz IS NULL OR (created_at,id)<($2,$3::uuid)) ORDER BY created_at DESC,id DESC LIMIT $4`,[conversationId,cursor?.at??null,cursor?.id??null,limit+1])).rows;
    const page=this.page(rows,limit,row=>({at:new Date(row.created_at).toISOString(),id:row.id}));
    return{items:page.items.reverse(),nextCursor:page.nextCursor};
  }

  @Get('likes') async likes(@Headers('authorization') authorization:string,@Query('cursor') rawCursor?:string,@Query('limit') rawLimit?:string){
    const id=this.userId(authorization),limit=limitOf(rawLimit),cursor=decodeCursor(rawCursor,true);
    const premium=Boolean((await this.db.query("SELECT 1 FROM subscriptions WHERE user_id=$1 AND tier='premium' AND (valid_until IS NULL OR valid_until>now())",[id])).rowCount);
    const visible=`l.to_user_id=$1
      AND source.active=true AND source.deleted_at IS NULL AND source.suspended_at IS NULL
      AND p.hidden=false AND pref.discoverable=true
      AND NOT EXISTS(SELECT 1 FROM blocks b WHERE (b.blocker_id=$1 AND b.blocked_id=l.from_user_id) OR (b.blocker_id=l.from_user_id AND b.blocked_id=$1))`;
    const count=Number((await this.db.query(`SELECT count(*) count FROM likes l JOIN users source ON source.id=l.from_user_id JOIN profiles p ON p.user_id=source.id JOIN preferences pref ON pref.user_id=source.id WHERE ${visible}`,[id])).rows[0].count);
    if(!premium)return{count,locked:true,items:[],nextCursor:null};
    const rows=(await this.db.query(`SELECT l.from_user_id user_id,p.name,extract(year from age(p.birth_date))::int age,l.kind,l.created_at,
      (SELECT '/media/'||ph.id::text||'/thumbnail' FROM photos ph WHERE ph.user_id=p.user_id AND ph.processing_status='ready' ORDER BY ph.position LIMIT 1) photo
      FROM likes l JOIN users source ON source.id=l.from_user_id JOIN profiles p ON p.user_id=source.id JOIN preferences pref ON pref.user_id=source.id
      WHERE ${visible} AND ($2::timestamptz IS NULL OR (l.created_at,l.from_user_id)<($2,$3::uuid))
      ORDER BY l.created_at DESC,l.from_user_id DESC LIMIT $4`,[id,cursor?.at??null,cursor?.id??null,limit+1])).rows;
    const page=this.page(rows,limit,row=>({at:new Date(row.created_at).toISOString(),id:row.user_id}));
    return{count,locked:false,...page};
  }
}

export const stage10Controllers=[PublicProfileController,PrivacyController,PaginatedFeedController];
