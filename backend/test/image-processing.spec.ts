import {describe,expect,it} from 'vitest';
import sharp from 'sharp';
import {InvalidImageError,processProfileImage} from '../src/image-processing';

describe('profile image processing',()=>{
  it('creates bounded JPEG variants and strips EXIF metadata',async()=>{
    const source=await sharp({
      create:{width:1600,height:1200,channels:3,background:{r:214,g:34,b:118}},
    }).withExif({IFD0:{Orientation:'6'},IFD3:{GPSLatitudeRef:'N',GPSLatitude:'55/1 45/1 0/1',GPSLongitudeRef:'E',GPSLongitude:'37/1 37/1 0/1'}}).jpeg({quality:95}).toBuffer();
    expect((await sharp(source).metadata()).exif).toBeDefined();

    const result=await processProfileImage(source,'image/jpeg');

    expect(result.thumbnail.width).toBeLessThanOrEqual(360);
    expect(result.medium.width).toBeLessThanOrEqual(1080);
    expect(result.full.width).toBeLessThanOrEqual(2400);
    for(const variant of [result.thumbnail,result.medium,result.full]){
      const metadata=await sharp(variant.buffer).metadata();
      expect(metadata.format).toBe('jpeg');
      expect(metadata.exif).toBeUndefined();
      expect(metadata.xmp).toBeUndefined();
      expect(metadata.iptc).toBeUndefined();
    }
  });

  it('rejects forged MIME types and corrupted payloads',async()=>{
    const png=await sharp({create:{width:32,height:32,channels:3,background:'magenta'}}).png().toBuffer();
    await expect(processProfileImage(png,'image/jpeg')).rejects.toMatchObject({code:'PHOTO_MIME_MISMATCH'} satisfies Partial<InvalidImageError>);
    await expect(processProfileImage(Buffer.from('not an image'),'image/jpeg')).rejects.toBeInstanceOf(InvalidImageError);
  });

  it('rejects images above the configured dimension ceiling',async()=>{
    const tooWide=await sharp({create:{width:12001,height:2,channels:3,background:'black'}}).png().toBuffer();
    await expect(processProfileImage(tooWide,'image/png')).rejects.toMatchObject({code:'PHOTO_DIMENSIONS_TOO_LARGE'} satisfies Partial<InvalidImageError>);
  });
});
