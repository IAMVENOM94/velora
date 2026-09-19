import sharp, {type Metadata,type Sharp} from 'sharp';
import {imageSignatureMatches} from './security';

const positiveInteger=(value:string|undefined,fallback:number)=>{const parsed=Number(value);return Number.isSafeInteger(parsed)&&parsed>0?parsed:fallback};
export const PROFILE_IMAGE_MAX_BYTES=positiveInteger(process.env.PROFILE_IMAGE_MAX_BYTES,10*1024*1024);
export const PROFILE_IMAGE_MAX_DIMENSION=positiveInteger(process.env.PROFILE_IMAGE_MAX_DIMENSION,12_000);
export const PROFILE_IMAGE_MAX_PIXELS=positiveInteger(process.env.PROFILE_IMAGE_MAX_PIXELS,40_000_000);

export type ProcessedImageVariant={
  buffer:Buffer;
  contentType:'image/jpeg';
  width:number;
  height:number;
};

export type ProcessedProfileImage={
  sourceMimeType:string;
  sourceSize:number;
  sourceWidth:number;
  sourceHeight:number;
  thumbnail:ProcessedImageVariant;
  medium:ProcessedImageVariant;
  full:ProcessedImageVariant;
};

export class InvalidImageError extends Error{
  constructor(public readonly code:string,message:string){super(message);this.name='InvalidImageError'}
}

const acceptedFormats=new Set(['jpeg','png','webp']);
const normalizedMime=(value:string)=>value.toLowerCase()==='image/jpg'?'image/jpeg':value.toLowerCase();

function validateMetadata(metadata:Metadata,declaredMime:string){
  if(!metadata.format||!acceptedFormats.has(metadata.format))throw new InvalidImageError('PHOTO_FORMAT_UNSUPPORTED','Only JPEG, PNG and WebP images are supported');
  const actualMime=`image/${metadata.format}`;
  if(actualMime!==declaredMime)throw new InvalidImageError('PHOTO_MIME_MISMATCH','The image content does not match its MIME type');
  const width=metadata.width??0;
  const height=metadata.height??0;
  if(width<1||height<1)throw new InvalidImageError('PHOTO_CORRUPTED','The image dimensions could not be read');
  if(width>PROFILE_IMAGE_MAX_DIMENSION||height>PROFILE_IMAGE_MAX_DIMENSION)throw new InvalidImageError('PHOTO_DIMENSIONS_TOO_LARGE',`Image dimensions must not exceed ${PROFILE_IMAGE_MAX_DIMENSION}px`);
  if(width*height>PROFILE_IMAGE_MAX_PIXELS)throw new InvalidImageError('PHOTO_PIXEL_LIMIT','The image contains too many pixels');
  return {width,height};
}

async function renderVariant(source:Sharp,maxWidth:number,maxHeight:number,quality:number):Promise<ProcessedImageVariant>{
  // rotate() applies EXIF orientation. Omitting withMetadata() deliberately strips all
  // EXIF/IPTC/XMP data, including GPS coordinates, from every public derivative.
  const {data,info}=await source
    .clone()
    .rotate()
    .resize({width:maxWidth,height:maxHeight,fit:'inside',withoutEnlargement:true})
    .flatten({background:{r:17,g:13,b:22}})
    .jpeg({quality,mozjpeg:true,progressive:true})
    .toBuffer({resolveWithObject:true});
  return {buffer:data,contentType:'image/jpeg',width:info.width,height:info.height};
}

export async function processProfileImage(bytes:Buffer,mimeType:string):Promise<ProcessedProfileImage>{
  const mime=normalizedMime(mimeType);
  if(bytes.length<12)throw new InvalidImageError('PHOTO_CORRUPTED','The image is empty or corrupted');
  if(bytes.length>PROFILE_IMAGE_MAX_BYTES)throw new InvalidImageError('PHOTO_TOO_LARGE',`Image size must not exceed ${PROFILE_IMAGE_MAX_BYTES} bytes`);
  if(!imageSignatureMatches(bytes,mime))throw new InvalidImageError('PHOTO_MIME_MISMATCH','The image signature does not match its MIME type');

  let metadata:Metadata;
  const source=sharp(bytes,{failOn:'error',limitInputPixels:PROFILE_IMAGE_MAX_PIXELS,sequentialRead:true});
  try{metadata=await source.metadata()}catch{throw new InvalidImageError('PHOTO_CORRUPTED','The image could not be decoded')}
  const dimensions=validateMetadata(metadata,mime);
  try{
    const [thumbnail,medium,full]=await Promise.all([
      renderVariant(source,360,480,78),
      renderVariant(source,1080,1440,84),
      renderVariant(source,2400,3200,88),
    ]);
    return {
      sourceMimeType:mime,
      sourceSize:bytes.length,
      sourceWidth:dimensions.width,
      sourceHeight:dimensions.height,
      thumbnail,medium,full,
    };
  }catch(error){
    if(error instanceof InvalidImageError)throw error;
    throw new InvalidImageError('PHOTO_PROCESSING_FAILED','The image could not be processed');
  }
}
