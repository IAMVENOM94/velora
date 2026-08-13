import {createCipheriv,createDecipheriv,createHash,createHmac,randomBytes,timingSafeEqual} from 'crypto';

export type IdentityProvider='email'|'google'|'phone'|'x'|'vk';
export type ProviderIdentity={provider:IdentityProvider;subject:string;email?:string;phone?:string;emailVerified?:boolean;phoneVerified?:boolean};

export const normalizePhone=(value:unknown)=>String(value??'').replace(/[\s()-]/g,'');
export const isE164=(value:string)=>/^\+[1-9][0-9]{7,14}$/.test(value);
export const normalizeLocale=(value:unknown)=>{const locale=String(value??'').toLowerCase().replace('_','-').split('-')[0];return locale==='ru'||locale==='en'?locale:null};
export const normalizeHandle=(value:unknown)=>String(value??'').trim().replace(/^@/,'').toLowerCase();
export const validHandle=(value:string)=>/^[a-z0-9._]{1,30}$/.test(value);

export const otpDigest=(phone:string,code:string,secret:string)=>createHmac('sha256',secret).update(`${phone}:${code}`).digest('hex');
export const randomOtp=()=>randomBytes(4).readUInt32BE(0).toString().padStart(10,'0').slice(-6);

const base64url=(value:Buffer)=>value.toString('base64url');
export const randomUrlToken=(bytes=32)=>base64url(randomBytes(bytes));
export const pkceChallenge=(verifier:string)=>base64url(createHash('sha256').update(verifier).digest());

function key(secret:string){return createHash('sha256').update(secret).digest()}
export function encryptState(value:string,secret:string){const iv=randomBytes(12);const cipher=createCipheriv('aes-256-gcm',key(secret),iv);const encrypted=Buffer.concat([cipher.update(value,'utf8'),cipher.final()]);return`${base64url(iv)}.${base64url(cipher.getAuthTag())}.${base64url(encrypted)}`}
export function decryptState(value:string,secret:string){const [iv,tag,data]=value.split('.').map(v=>Buffer.from(v,'base64url'));const decipher=createDecipheriv('aes-256-gcm',key(secret),iv);decipher.setAuthTag(tag);return Buffer.concat([decipher.update(data),decipher.final()]).toString('utf8')}

export function createDevelopmentAssertion(identity:ProviderIdentity,secret:string){const payload=Buffer.from(JSON.stringify(identity)).toString('base64url');const signature=createHmac('sha256',secret).update(payload).digest('base64url');return`dev.${payload}.${signature}`}
export function verifyDevelopmentAssertion(token:string,expected:IdentityProvider,secret:string):ProviderIdentity|null{const [prefix,payload,signature]=String(token??'').split('.');if(prefix!=='dev'||!payload||!signature)return null;const expectedSignature=createHmac('sha256',secret).update(payload).digest();const supplied=Buffer.from(signature,'base64url');if(supplied.length!==expectedSignature.length||!timingSafeEqual(supplied,expectedSignature))return null;try{const parsed=JSON.parse(Buffer.from(payload,'base64url').toString('utf8')) as ProviderIdentity;return parsed.provider===expected&&typeof parsed.subject==='string'&&parsed.subject.length>0?parsed:null}catch{return null}}
