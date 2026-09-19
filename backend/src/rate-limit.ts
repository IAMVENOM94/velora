import {CanActivate,ExecutionContext,HttpException,Injectable} from '@nestjs/common';
import type {Request,Response} from 'express';
import * as jwt from 'jsonwebtoken';
import {accessSecret,Db,type JwtUser} from './platform';
import {hashToken} from './security';

export type RateRule={scope:string;limit:number;windowSeconds:number;anonymousField?:string;ipLimit?:number};

export function rateRule(method:string,path:string):RateRule|null{
  // Authentication is limited both per normalized account identifier and per IP.
  // The wider IP ceiling avoids punishing households, offices and integration
  // runners that legitimately share a NAT while still bounding bot traffic.
  if(method==='POST'&&path==='/auth/login')return{scope:'login',limit:12,windowSeconds:15*60,anonymousField:'email',ipLimit:120};
  if(method==='POST'&&path==='/auth/register')return{scope:'register',limit:5,windowSeconds:60*60,anonymousField:'email',ipLimit:60};
  if(method==='POST'&&path==='/auth/phone/request')return{scope:'otp',limit:20,windowSeconds:15*60};
  if(method==='GET'&&/^\/users\/[0-9a-f-]{36}\/profile$/i.test(path))return{scope:'profile_view',limit:180,windowSeconds:60};
  if(method==='POST'&&/^\/likes\/[0-9a-f-]{36}$/i.test(path))return{scope:'like',limit:100,windowSeconds:60*60};
  if(method==='POST'&&/^\/passes\/[0-9a-f-]{36}$/i.test(path))return{scope:'pass',limit:200,windowSeconds:60*60};
  if(method==='POST'&&/^\/conversations\/[0-9a-f-]{36}\/messages(?:\/photo)?$/i.test(path))return{scope:'message',limit:120,windowSeconds:60};
  if(method==='POST'&&path==='/reports')return{scope:'report',limit:10,windowSeconds:24*60*60};
  if(method==='POST'&&(/^\/users\/me\/photos\/[0-8]\/upload$/i.test(path)||path==='/users/me/photos'||path==='/users/me/photos/upload-url'))return{scope:'photo_upload',limit:30,windowSeconds:60*60};
  return null;
}

@Injectable()
export class ScopedRateLimitGuard implements CanActivate{
  constructor(private db:Db){}
  async canActivate(context:ExecutionContext){
    const request=context.switchToHttp().getRequest<Request>();
    const response=context.switchToHttp().getResponse<Response>();
    const rule=rateRule(request.method,request.path);
    if(!rule)return true;
    let userId:string|undefined;const authorization=request.header('authorization');
    if(authorization?.startsWith('Bearer ')){try{userId=(jwt.verify(authorization.slice(7),accessSecret()) as JwtUser).sub}catch{}}
    const ip=(request.ip||request.socket.remoteAddress||'unknown').replace(/^::ffff:/,'');
    const anonymousValue=rule.anonymousField&&typeof (request.body as any)?.[rule.anonymousField]==='string'
      ?String((request.body as any)[rule.anonymousField]).trim().toLowerCase()
      :undefined;
    const configuredMultiplier=Number(process.env.RATE_LIMIT_MULTIPLIER??(process.env.NODE_ENV==='production'?'1':'10'));
    const multiplier=Number.isFinite(configuredMultiplier)?Math.min(100,Math.max(1,configuredMultiplier)):1;
    const userLimit=Math.floor(rule.limit*multiplier),ipLimit=Math.floor((rule.ipLimit??rule.limit)*multiplier);
    const subjects=userId
      ?[{scope:rule.scope,hash:hashToken(`user:${userId}`),limit:userLimit}]
      :anonymousValue
        ?[
          {scope:rule.scope,hash:hashToken(`identifier:${anonymousValue}`),limit:userLimit},
          {scope:`${rule.scope}_ip`,hash:hashToken(`ip:${ip}`),limit:ipLimit},
        ]
        :[{scope:rule.scope,hash:hashToken(`ip:${ip}`),limit:userLimit}];
    const client=await this.db.pool.connect();
    try{
      await client.query('BEGIN');
      for(const subject of [...subjects].sort((left,right)=>`${left.scope}:${left.hash}`.localeCompare(`${right.scope}:${right.hash}`)))await client.query('SELECT pg_advisory_xact_lock(hashtext($1))',[`${subject.scope}:${subject.hash}`]);
      let remaining=userLimit;
      for(const subject of subjects){
        const count=Number((await client.query("SELECT count(*) count FROM rate_limit_events WHERE scope=$1 AND subject_hash=$2 AND created_at>now()-($3||' seconds')::interval",[subject.scope,subject.hash,rule.windowSeconds])).rows[0].count);
        if(subject.scope===rule.scope)remaining=Math.max(0,subject.limit-count-1);
        if(count>=subject.limit){response.setHeader('Retry-After',rule.windowSeconds);throw new HttpException({code:'RATE_LIMITED',message:'Too many requests. Please try again later.'},429)}
      }
      response.setHeader('X-RateLimit-Limit',userLimit);
      response.setHeader('X-RateLimit-Remaining',remaining);
      for(const subject of subjects)await client.query('INSERT INTO rate_limit_events(scope,subject_hash) VALUES($1,$2)',[subject.scope,subject.hash]);
      await client.query('COMMIT');return true;
    }catch(error){await client.query('ROLLBACK');throw error}finally{client.release()}
  }
}
