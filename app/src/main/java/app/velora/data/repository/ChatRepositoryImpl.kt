package app.velora.data.repository

import app.velora.BuildConfig
import app.velora.data.local.ResponseCache
import app.velora.data.local.TokenStore
import app.velora.data.remote.*
import app.velora.domain.model.*
import app.velora.domain.repository.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.retryWhen
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class SocketEnvelope(val type:String,val data:MessageDto?=null,@SerialName("userId")val userId:String?=null,val active:Boolean=false,val message:String?=null,val messageIds:List<String> = emptyList())

@Singleton class ChatRepositoryImpl @Inject constructor(private val api:VeloraApi,private val cache:ResponseCache,private val json:Json,private val client:OkHttpClient,private val tokens:TokenStore):ChatRepository{
    private val sockets=ConcurrentHashMap<String,WebSocket>()
    override suspend fun conversations():DataResult<List<Conversation>> = when(val r=remoteCall{api.conversations()}){is DataResult.Success->{cache.put("conversations",json.encodeToString(ListSerializer(ConversationDto.serializer()),r.value));DataResult.Success(r.value.map{it.domain()})};is DataResult.Failure->{val c=cache.get("conversations")?.let{runCatching{json.decodeFromString(ListSerializer(ConversationDto.serializer()),it)}.getOrNull()};if(c!=null)DataResult.Success(c.map{it.domain()},true)else r}}
    override suspend fun messages(conversationId:String):DataResult<List<ChatMessage>> = when(val r=remoteCall{api.messages(conversationId)}){is DataResult.Success->{cache.put("messages_$conversationId",json.encodeToString(ListSerializer(MessageDto.serializer()),r.value));DataResult.Success(r.value.map{it.domain()})};is DataResult.Failure->{val c=cache.get("messages_$conversationId")?.let{runCatching{json.decodeFromString(ListSerializer(MessageDto.serializer()),it)}.getOrNull()};if(c!=null)DataResult.Success(c.map{it.domain()},true)else r}}
    override suspend fun send(conversationId:String,body:String,replyToId:String?)=when(val r=remoteCall{api.send(conversationId,SendMessageDto(body,replyToId))}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}
    override suspend fun sendPhoto(conversationId:String,bytes:ByteArray,mimeType:String,replyToId:String?)=when(val r=remoteCall{api.sendPhoto(conversationId,replyToId,MultipartBody.Part.createFormData("file",if(mimeType=="image/png")"chat.png" else if(mimeType=="image/webp")"chat.webp" else "chat.jpg",bytes.toRequestBody(mimeType.toMediaType())) )}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}
    override suspend fun markRead(conversationId:String)=when(val r=remoteCall{api.read(conversationId)}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    override fun sendTyping(conversationId:String,active:Boolean){sockets[conversationId]?.send(json.encodeToString(SocketEnvelope.serializer(),SocketEnvelope(type="typing",active=active)))}
    override fun realtime(conversationId:String):Flow<ChatEvent> = callbackFlow {val token=runBlocking{tokens.current()?.access};if(token==null){trySend(ChatEvent.Error("No active session"));close();return@callbackFlow};val url=(BuildConfig.API_BASE_URL+"ws/chat").toHttpUrl().newBuilder().addQueryParameter("token",token).addQueryParameter("conversationId",conversationId).build();val socket=client.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){override fun onMessage(webSocket:WebSocket,text:String){runCatching{json.decodeFromString(SocketEnvelope.serializer(),text)}.onSuccess{e->when(e.type){"connected"->{sockets[conversationId]=webSocket;trySend(ChatEvent.Connected)};"message"->e.data?.let{trySend(ChatEvent.Message(it.domain()))};"typing"->trySend(ChatEvent.Typing(e.userId.orEmpty(),e.active));"online"->trySend(ChatEvent.Online(e.userId.orEmpty(),e.active));"read"->trySend(ChatEvent.Read(e.userId.orEmpty(),e.messageIds));"error"->trySend(ChatEvent.Error(e.message?:"Realtime error"))}}};override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){sockets.remove(conversationId,webSocket);trySend(ChatEvent.Error(t.message?:"Realtime disconnected"));close(t)};override fun onClosed(webSocket:WebSocket,code:Int,reason:String){sockets.remove(conversationId,webSocket);close()}});awaitClose{sockets.remove(conversationId,socket);socket.close(1000,"Screen closed")}}.retryWhen{_,attempt->delay((1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L));true}
    private fun ConversationDto.domain()=Conversation(id,name,url(photo),lastMessage,lastMessageAt,unreadCount,matchId,otherUserId)
    private fun MessageDto.domain()=ChatMessage(id,conversationId,senderId,body,mediaKey,replyToId,createdAt,readAt,url(mediaUrl))
    private fun url(value:String?)=value?.let{if(it.startsWith("http"))it else BuildConfig.API_BASE_URL.removeSuffix("/")+it}
}
