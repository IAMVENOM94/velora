import {describe,expect,it} from 'vitest';
import {createDevelopmentAssertion,decryptState,encryptState,isE164,normalizeHandle,normalizeLocale,normalizePhone,otpDigest,pkceChallenge,verifyDevelopmentAssertion} from '../src/stage9-security';

describe('Stage 9 security primitives',()=>{
  it('normalizes only valid E.164 phone numbers',()=>{expect(normalizePhone('+7 (999) 123-45-67')).toBe('+79991234567');expect(isE164('+79991234567')).toBe(true);expect(isE164('89991234567')).toBe(false)});
  it('normalizes supported locales without a client locale engine',()=>{expect(normalizeLocale('ru-RU')).toBe('ru');expect(normalizeLocale('en_US')).toBe('en');expect(normalizeLocale('pl')).toBeNull()});
  it('normalizes safe social handles',()=>{expect(normalizeHandle(' @Velora.Team ')).toBe('velora.team')});
  it('peppers OTP values and binds them to a phone',()=>{expect(otpDigest('+79990000000','123456','secret')).not.toBe(otpDigest('+79990000001','123456','secret'))});
  it('round-trips encrypted OAuth state and rejects the wrong key',()=>{const value=encryptState('verifier','state-secret');expect(decryptState(value,'state-secret')).toBe('verifier');expect(()=>decryptState(value,'other-secret')).toThrow()});
  it('creates deterministic S256 PKCE challenges',()=>{expect(pkceChallenge('test-verifier')).toMatch(/^[A-Za-z0-9_-]{43}$/)});
  it('accepts signed development provider assertions and rejects tampering',()=>{const token=createDevelopmentAssertion({provider:'google',subject:'subject-1',email:'person@example.com'},'dev-secret');expect(verifyDevelopmentAssertion(token,'google','dev-secret')?.subject).toBe('subject-1');expect(verifyDevelopmentAssertion(token+'x','google','dev-secret')).toBeNull();expect(verifyDevelopmentAssertion(token,'x','dev-secret')).toBeNull()});
});
