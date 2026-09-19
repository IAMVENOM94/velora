import {Injectable} from '@nestjs/common';
import {WebSocket} from 'ws';

export type ChatSocket=WebSocket&{
  userId?:string;
  conversationId?:string;
  alive?:boolean;
  presenceVisible?:boolean;
  lastPresenceAt?:number;
};

@Injectable()
export class RealtimeHub{
  private readonly rooms=new Map<string,Set<ChatSocket>>();

  add(conversationId:string,socket:ChatSocket){if(!this.rooms.has(conversationId))this.rooms.set(conversationId,new Set());this.rooms.get(conversationId)!.add(socket)}
  remove(conversationId:string,socket:ChatSocket){this.rooms.get(conversationId)?.delete(socket);if(!this.rooms.get(conversationId)?.size)this.rooms.delete(conversationId)}
  broadcast(conversationId:string,event:Record<string,unknown>,exclude?:ChatSocket){for(const socket of this.rooms.get(conversationId)??[])if(socket!==exclude&&socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify(event))}
  closeUser(userId:string){for(const room of this.rooms.values())for(const socket of room)if(socket.userId===userId)socket.close(1008,'Account unavailable')}
  closeConversation(conversationId:string,reason:string){for(const socket of this.rooms.get(conversationId)??[])socket.close(1008,reason)}
  hasOpenSocket(userId:string,exclude?:ChatSocket){for(const room of this.rooms.values())for(const socket of room)if(socket!==exclude&&socket.userId===userId&&socket.readyState===WebSocket.OPEN)return true;return false}
  hasOpenPeer(conversationId:string,userId:string){for(const socket of this.rooms.get(conversationId)??[])if(socket.userId!==userId&&socket.readyState===WebSocket.OPEN)return true;return false}
  setPresenceVisibility(userId:string,visible:boolean){
    const conversations=new Set<string>();
    for(const [conversationId,room] of this.rooms)for(const socket of room)if(socket.userId===userId){socket.presenceVisible=visible;conversations.add(conversationId)}
    for(const conversationId of conversations)this.broadcast(conversationId,{type:'online',userId,active:visible});
  }
}
