import {describe,expect,it} from 'vitest';
import {hashToken,imageSignatureMatches,isUuid,isValidEmail,isValidPassword,normalizeEmail} from '../src/security';

describe('security primitives',()=>{
  it('normalizes and validates bounded email addresses',()=>{
    expect(normalizeEmail('  Person@Example.COM ')).toBe('person@example.com');
    expect(isValidEmail('person@example.com')).toBe(true);
    expect(isValidEmail('not-an-email')).toBe(false);
    expect(isValidEmail(`${'a'.repeat(245)}@test.com`)).toBe(false);
  });

  it('enforces password length boundaries',()=>{
    expect(isValidPassword('1234567')).toBe(false);
    expect(isValidPassword('12345678')).toBe(true);
    expect(isValidPassword('x'.repeat(128))).toBe(true);
    expect(isValidPassword('x'.repeat(129))).toBe(false);
  });

  it('recognizes UUIDs and rejects database-shaped garbage',()=>{
    expect(isUuid('123e4567-e89b-42d3-a456-426614174000')).toBe(true);
    expect(isUuid("' OR true --")).toBe(false);
  });

  it('hashes tokens deterministically without storing plaintext',()=>{
    const first=hashToken('secret-token');
    expect(first).toHaveLength(64);
    expect(first).toBe(hashToken('secret-token'));
    expect(first).not.toBe(hashToken('another-token'));
    expect(first).not.toContain('secret-token');
  });

  it('matches image magic bytes to the declared type',()=>{
    const png=Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]);
    const jpeg=Buffer.from([0xff,0xd8,0xff,0x00]);
    const webp=Buffer.from('RIFF0000WEBP');
    expect(imageSignatureMatches(png,'image/png')).toBe(true);
    expect(imageSignatureMatches(jpeg,'jpg')).toBe(true);
    expect(imageSignatureMatches(webp,'webp')).toBe(true);
    expect(imageSignatureMatches(png,'image/jpeg')).toBe(false);
    expect(imageSignatureMatches(Buffer.from('script'),'png')).toBe(false);
  });
});
