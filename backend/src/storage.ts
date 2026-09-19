import {HttpException,Injectable} from '@nestjs/common';
import {randomUUID} from 'crypto';
import {Client as MinioClient} from 'minio';
import {InvalidImageError,processProfileImage,PROFILE_IMAGE_MAX_BYTES,type ProcessedProfileImage} from './image-processing';
import {configured} from './platform';

const configuredPhotoLimit=Number(process.env.PROFILE_PHOTO_LIMIT??9);
export const PROFILE_PHOTO_LIMIT=Number.isInteger(configuredPhotoLimit)?Math.min(9,Math.max(1,configuredPhotoLimit)):9;

export type StoredProfileImage={
  objectKey:string;
  thumbnailKey:string;
  mediumKey:string;
  fullKey:string;
  mimeType:'image/jpeg';
  sourceSize:number;
  width:number;
  height:number;
};

@Injectable()
export class StorageService{
  private client:MinioClient;
  private signer:MinioClient;
  private bucket=process.env.MINIO_BUCKET??'velora-photos';
  private ready:Promise<void>;
  constructor(){
    const endpoint=new URL(configured('MINIO_ENDPOINT'));
    const publicEndpoint=new URL(process.env.MINIO_PUBLIC_ENDPOINT??configured('MINIO_ENDPOINT'));
    const accessKey=configured('MINIO_ACCESS_KEY');
    const secretKey=configured('MINIO_SECRET_KEY');
    const options=(value:URL)=>({endPoint:value.hostname,port:Number(value.port||(value.protocol==='https:'?443:80)),useSSL:value.protocol==='https:',accessKey,secretKey,region:process.env.MINIO_REGION??'us-east-1'});
    this.client=new MinioClient(options(endpoint));
    this.signer=new MinioClient(options(publicEndpoint));
    this.ready=this.ensureBucket();
  }

  private async ensureBucket(){let lastError:unknown;for(let attempt=0;attempt<20;attempt++){try{if(!await this.client.bucketExists(this.bucket))await this.client.makeBucket(this.bucket);return}catch(error){lastError=error;await new Promise(resolve=>setTimeout(resolve,Math.min(250*(attempt+1),2_000)))}}throw lastError}
  private imageError(error:unknown):never{if(error instanceof HttpException)throw error;if(error instanceof InvalidImageError)throw new HttpException({code:error.code,message:error.message},error.code==='PHOTO_TOO_LARGE'?413:400);throw new HttpException({code:'PHOTO_PROCESSING_FAILED',message:'The image could not be processed'},400)}
  private mimeFromKey(key:string){const extension=key.split('.').pop()?.toLowerCase();return extension==='png'?'image/png':extension==='webp'?'image/webp':'image/jpeg'}
  private async buffer(objectKey:string){
    await this.ready;
    const stat=await this.client.statObject(this.bucket,objectKey);
    if(stat.size<1||stat.size>PROFILE_IMAGE_MAX_BYTES)throw new InvalidImageError('PHOTO_TOO_LARGE',`Image size must not exceed ${PROFILE_IMAGE_MAX_BYTES} bytes`);
    const stream=await this.client.getObject(this.bucket,objectKey);const chunks:Buffer[]=[];let size=0;
    for await(const chunk of stream){const value=Buffer.from(chunk);size+=value.length;if(size>PROFILE_IMAGE_MAX_BYTES)throw new InvalidImageError('PHOTO_TOO_LARGE',`Image size must not exceed ${PROFILE_IMAGE_MAX_BYTES} bytes`);chunks.push(value)}
    return{bytes:Buffer.concat(chunks),mimeType:String(stat.metaData?.['content-type']??this.mimeFromKey(objectKey)).toLowerCase()};
  }
  private async storeProcessed(userId:string,image:ProcessedProfileImage):Promise<StoredProfileImage>{
    const root=`${userId}/profile/${randomUUID()}`;
    const thumbnailKey=`${root}/thumbnail.jpg`,mediumKey=`${root}/medium.jpg`,fullKey=`${root}/full.jpg`;
    const keys=[thumbnailKey,mediumKey,fullKey];
    try{
      await Promise.all([
        this.client.putObject(this.bucket,thumbnailKey,image.thumbnail.buffer,image.thumbnail.buffer.length,{'Content-Type':'image/jpeg','Cache-Control':'private,max-age=31536000,immutable'}),
        this.client.putObject(this.bucket,mediumKey,image.medium.buffer,image.medium.buffer.length,{'Content-Type':'image/jpeg','Cache-Control':'private,max-age=31536000,immutable'}),
        this.client.putObject(this.bucket,fullKey,image.full.buffer,image.full.buffer.length,{'Content-Type':'image/jpeg','Cache-Control':'private,max-age=31536000,immutable'}),
      ]);
      return{objectKey:fullKey,thumbnailKey,mediumKey,fullKey,mimeType:'image/jpeg',sourceSize:image.sourceSize,width:image.sourceWidth,height:image.sourceHeight};
    }catch(error){await this.removeMany(keys).catch(()=>{});throw error}
  }
  private async process(userId:string,bytes:Buffer,mimeType:string){await this.ready;try{return await this.storeProcessed(userId,await processProfileImage(bytes,mimeType))}catch(error){this.imageError(error)}}

  async uploadUrl(userId:string,extension='jpg'){
    await this.ready;const safe=extension.toLowerCase().replace(/[^a-z0-9]/g,'');
    if(!['jpg','jpeg','png','webp'].includes(safe))throw new HttpException({code:'PHOTO_FORMAT_UNSUPPORTED',message:'JPEG, PNG or WebP required'},400);
    const objectKey=`${userId}/incoming/${randomUUID()}.${safe==='jpeg'?'jpg':safe}`;
    return{objectKey,uploadUrl:await this.signer.presignedPutObject(this.bucket,objectKey,15*60),maxBytes:PROFILE_IMAGE_MAX_BYTES};
  }
  async putProfile(userId:string,file:Express.Multer.File){return this.process(userId,file.buffer,file.mimetype)}
  async finalizeProfile(userId:string,objectKey:string){
    if(!objectKey.startsWith(`${userId}/incoming/`))throw new HttpException({code:'PHOTO_UPLOAD_INVALID',message:'Uploaded object does not belong to this user'},400);
    try{const source=await this.buffer(objectKey);const stored=await this.process(userId,source.bytes,source.mimeType);await this.remove(objectKey).catch(()=>{});return stored}catch(error){await this.remove(objectKey).catch(()=>{});this.imageError(error)}
  }
  async migrateLegacy(userId:string,objectKey:string){const source=await this.buffer(objectKey);return this.process(userId,source.bytes,source.mimeType)}
  async put(userPath:string,file:Express.Multer.File){
    const owner=userPath.split('/')[0];const processed=await processProfileImage(file.buffer,file.mimetype).catch(error=>this.imageError(error));
    const objectKey=`${owner}/chat/${randomUUID()}.jpg`;
    await this.ready;await this.client.putObject(this.bucket,objectKey,processed.full.buffer,processed.full.buffer.length,{'Content-Type':'image/jpeg'});return objectKey;
  }
  async stream(objectKey:string){await this.ready;return this.client.getObject(this.bucket,objectKey)}
  async remove(objectKey:string){await this.ready;await this.client.removeObject(this.bucket,objectKey)}
  async removeMany(objectKeys:Array<string|null|undefined>){await this.ready;const keys=[...new Set(objectKeys.filter((value):value is string=>Boolean(value)))];if(keys.length)await this.client.removeObjects(this.bucket,keys)}
}
