import {describe,expect,it} from 'vitest';
import {presenceOf} from '../src/stage10';
import {rateRule} from '../src/rate-limit';

describe('Stage 10 policy helpers',()=>{
  it('redacts presence when privacy is disabled',()=>{expect(presenceOf(new Date().toISOString(),false,true)).toEqual({onlineStatus:'HIDDEN',lastSeen:null})});
  it('uses the authoritative socket flag and classifies last seen',()=>{const now=Date.parse('2026-08-14T12:00:00Z');expect(presenceOf('2026-08-13T12:00:00Z',true,true,now).onlineStatus).toBe('ONLINE');expect(presenceOf('2026-08-14T11:59:00Z',true,false,now).onlineStatus).toBe('RECENTLY_ONLINE');expect(presenceOf('2026-08-13T12:00:00Z',true,false,now).onlineStatus).toBe('LAST_SEEN')});
  it('uses distinct limits for abuse-sensitive operations',()=>{expect(rateRule('POST','/auth/login')?.scope).toBe('login');expect(rateRule('POST','/likes/00000000-0000-4000-8000-000000000001')?.scope).toBe('like');expect(rateRule('POST','/reports')?.limit).toBe(10);expect(rateRule('GET','/health')).toBeNull()});
});
