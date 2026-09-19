import {ArgumentsHost,Catch,ExceptionFilter,HttpException} from '@nestjs/common';
import {randomUUID} from 'crypto';
import type {NextFunction,Request,Response} from 'express';

const requestIdPattern=/^[a-zA-Z0-9._-]{8,80}$/;
const statusCode=(status:number)=>status===400?'BAD_REQUEST':status===401?'UNAUTHORIZED':status===403?'FORBIDDEN':status===404?'NOT_FOUND':status===409?'CONFLICT':status===413?'PAYLOAD_TOO_LARGE':status===429?'RATE_LIMITED':status>=500?'INTERNAL_ERROR':'REQUEST_FAILED';

export function requestContext(request:Request,response:Response,next:NextFunction){
  const supplied=request.header('x-request-id');
  const requestId=supplied&&requestIdPattern.test(supplied)?supplied:randomUUID();
  response.locals.requestId=requestId;
  response.setHeader('X-Request-Id',requestId);
  const started=performance.now();
  response.once('finish',()=>console.info(JSON.stringify({level:'info',event:'http_request',requestId,method:request.method,path:request.path,status:response.statusCode,durationMs:Math.round(performance.now()-started)})));
  next();
}

@Catch()
export class ApiExceptionFilter implements ExceptionFilter{
  catch(exception:unknown,host:ArgumentsHost){
    const response=host.switchToHttp().getResponse<Response>();
    const request=host.switchToHttp().getRequest<Request>();
    const requestId=String(response.locals.requestId??randomUUID());
    const status=exception instanceof HttpException?exception.getStatus():500;
    const source=exception instanceof HttpException?exception.getResponse():null;
    const detail=typeof source==='object'&&source!==null?source as Record<string,unknown>:{};
    const rawMessage=typeof source==='string'?source:typeof detail.message==='string'?detail.message:status>=500?'Something went wrong':'Request failed';
    const message=status>=500?'Something went wrong':rawMessage;
    if(status>=500)console.error(JSON.stringify({level:'error',event:'request_failed',requestId,method:request.method,path:request.path,status,errorType:exception instanceof Error?exception.name:'unknown'}));
    response.status(status).json({code:typeof detail.code==='string'?detail.code:statusCode(status),message,...(detail.fieldErrors&&typeof detail.fieldErrors==='object'?{fieldErrors:detail.fieldErrors}:{}),requestId});
  }
}
