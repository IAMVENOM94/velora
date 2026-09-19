import {CanActivate,ExecutionContext,HttpException,Injectable,OnModuleDestroy} from '@nestjs/common';
import {Pool} from 'pg';
import * as jwt from 'jsonwebtoken';
import {randomUUID} from 'crypto';
import {hashToken} from './security';

export type JwtUser={sub:string,email?:string};

export const configured=(name:string)=>{const value=process.env[name];if(!value)throw new Error(`${name} is required`);const normalized=value.toLowerCase();if(process.env.NODE_ENV==='production'&&(value.length<16||normalized.includes('replace')||normalized.includes('change-me')))throw new Error(`${name} must be configured securely`);return value};
const jwtSecret=(name:string)=>{const value=configured(name);if(process.env.NODE_ENV==='production'&&value.length<32)throw new Error(`${name} must contain at least 32 characters`);return value};
export const accessSecret=()=>jwtSecret('JWT_ACCESS_SECRET');
export const refreshSecret=()=>jwtSecret('JWT_REFRESH_SECRET');

@Injectable()
export class Db implements OnModuleDestroy {
  pool=new Pool({connectionString:configured('DATABASE_URL')});
  query(sql:string,args:any[]=[]){return this.pool.query(sql,args)}
  async onModuleDestroy(){await this.pool.end()}
}

@Injectable()
export class AuthService {
  constructor(private db:Db){}
  access(user:JwtUser){return jwt.sign(user,accessSecret(),{expiresIn:(process.env.ACCESS_TTL??'15m') as any})}
  async pair(user:JwtUser){const refresh=jwt.sign({...user,jti:randomUUID()},refreshSecret(),{expiresIn:`${process.env.REFRESH_TTL_DAYS??30}d` as any});await this.db.query('INSERT INTO refresh_tokens(user_id,token_hash,expires_at) VALUES($1,$2,now()+($3||\' days\')::interval)',[user.sub,hashToken(refresh),process.env.REFRESH_TTL_DAYS??30]);return{accessToken:this.access(user),refreshToken:refresh}}
  user(auth?:string):JwtUser{if(!auth?.startsWith('Bearer '))throw new HttpException('Unauthorized',401);try{return jwt.verify(auth.slice(7),accessSecret()) as JwtUser}catch{throw new HttpException('Unauthorized',401)}}
}

@Injectable()
export class ActiveUserGuard implements CanActivate {
  constructor(private auth:AuthService,private db:Db){}
  async canActivate(context:ExecutionContext){const request=context.switchToHttp().getRequest();const user=this.auth.user(request.headers.authorization);const active=await this.db.query('SELECT 1 FROM users WHERE id=$1 AND active=true AND deleted_at IS NULL AND suspended_at IS NULL',[user.sub]);if(!active.rowCount)throw new HttpException({code:'SESSION_INVALID',message:'Unauthorized'},401);request.user=user;return true}
}
