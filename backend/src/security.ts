import {createHash} from 'crypto';

const emailPattern=/^[^\s@]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+$/i;
const uuidPattern=/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export const hashToken=(value:string)=>createHash('sha256').update(value).digest('hex');
export const normalizeEmail=(value:unknown)=>String(value??'').trim().toLowerCase();
export const isValidEmail=(value:string)=>{if(value.length>254||!emailPattern.test(value))return false;const [local,domain]=value.split('@');return local.length<=64&&domain.length<=253&&domain.split('.').every(label=>label.length>=1&&label.length<=63&&!label.startsWith('-')&&!label.endsWith('-'))};
export const isValidPassword=(value:unknown)=>typeof value==='string'&&value.length>=8&&value.length<=128;
export const isUuid=(value:unknown)=>uuidPattern.test(String(value??''));

export function imageSignatureMatches(bytes:Buffer,type:string|undefined){
  const kind=String(type??'').toLowerCase().replace('image/','');
  const jpeg=bytes.length>=3&&bytes[0]===0xff&&bytes[1]===0xd8&&bytes[2]===0xff;
  const png=bytes.length>=8&&bytes.subarray(0,8).equals(Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]));
  const webp=bytes.length>=12&&bytes.subarray(0,4).toString()==='RIFF'&&bytes.subarray(8,12).toString()==='WEBP';
  return kind==='png'?png:kind==='webp'?webp:kind==='jpg'||kind==='jpeg'?jpeg:false;
}
