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
import java.util.UUID
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class SocketEnvelope(val type:String,val data:MessageDto?=null,@SerialName("userId")val userId:String?=null,val active:Boolean=false,val message:String?=null,val messageIds:List<String> = emptyList())
internal fun reconnectDelayMillis(attempt:Long)=(1_000L shl attempt.coerceAtMost(5).toInt()).coerceAtMost(30_000L)

@Singleton class ChatRepositoryImpl @Inject constructor(private val api:VeloraApi,private val cache:ResponseCache,private val json:Json,private val client:OkHttpClient,private val tokens:TokenStore):ChatRepository{
    private val sockets=ConcurrentHashMap<String,WebSocket>()
    override suspend fun conversations():DataResult<List<Conversation>> = when(val page=conversationsPage()){is DataResult.Success->DataResult.Success(page.value.items,page.fromCache);is DataResult.Failure->page}
    override suspend fun conversationsPage(cursor:String?):DataResult<CursorPage<Conversation>> = when(val r=remoteCall{api.conversationsPage(cursor)}){is DataResult.Success->{if(cursor==null)cache.put("conversations",json.encodeToString(ListSerializer(ConversationDto.serializer()),r.value.items));DataResult.Success(CursorPage(r.value.items.map{it.domain()},r.value.nextCursor))};is DataResult.Failure->{if(!r.retryable)cache.remove("conversations");if(cursor!=null||!r.retryable)r else{val c=cache.get("conversations")?.let{runCatching{json.decodeFromString(ListSerializer(ConversationDto.serializer()),it)}.getOrNull()};if(c!=null)DataResult.Success(CursorPage(c.map{it.domain()},null),true)else r}}}
    override suspend fun messages(conversationId:String):DataResult<List<ChatMessage>> = when(val page=messagesPage(conversationId)){is DataResult.Success->DataResult.Success(page.value.items,page.fromCache);is DataResult.Failure->page}
    override suspend fun messagesPage(conversationId:String,cursor:String?):DataResult<CursorPage<ChatMessage>> = when(val r=remoteCall{api.messagesPage(conversationId,cursor)}){is DataResult.Success->{if(cursor==null)cache.put("messages_$conversationId",json.encodeToString(ListSerializer(MessageDto.serializer()),r.value.items));DataResult.Success(CursorPage(r.value.items.map{it.domain()},r.value.nextCursor))};is DataResult.Failure->{if(!r.retryable)cache.remove("messages_$conversationId");if(cursor!=null||!r.retryable)r else{val c=cache.get("messages_$conversationId")?.let{runCatching{json.decodeFromString(ListSerializer(MessageDto.serializer()),it)}.getOrNull()};if(c!=null)DataResult.Success(CursorPage(c.map{it.domain()},null),true)else r}}}
    override suspend fun send(conversationId:String,body:String,replyToId:String?):DataResult<ChatMessage>{val clientId=UUID.randomUUID().toString();return when(val r=remoteCall{api.send(conversationId,SendMessageDto(body,replyToId,clientId))}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}}
    override suspend fun sendPhoto(conversationId:String,bytes:ByteArray,mimeType:String,replyToId:String?):DataResult<ChatMessage>{val clientId=UUID.randomUUID().toString();return when(val r=remoteCall{api.sendPhoto(conversationId,replyToId,clientId,MultipartBody.Part.createFormData("file",if(mimeType=="image/png")"chat.png" else if(mimeType=="image/webp")"chat.webp" else "chat.jpg",bytes.toRequestBody(mimeType.toMediaType())) )}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}}
    override suspend fun markRead(conversationId:String)=when(val r=remoteCall{api.read(conversationId)}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    override fun sendTyping(conversationId:String,active:Boolean){sockets[conversationId]?.send(json.encodeToString(SocketEnvelope.serializer(),SocketEnvelope(type="typing",active=active)))}
    override fun realtime(conversationId:String):Flow<ChatEvent> = callbackFlow {val token=runBlocking{tokens.current()?.access};if(token==null){trySend(ChatEvent.Error("No active session"));close();return@callbackFlow};val url=(BuildConfig.API_BASE_URL+"ws/chat").toHttpUrl().newBuilder().addQueryParameter("conversationId",conversationId).build();val socket=client.newWebSocket(Request.Builder().url(url).header("Authorization","Bearer $token").build(),object:WebSocketListener(){override fun onMessage(webSocket:WebSocket,text:String){runCatching{json.decodeFromString(SocketEnvelope.serializer(),text)}.onSuccess{e->when(e.type){"connected"->{sockets[conversationId]=webSocket;trySend(ChatEvent.Connected)};"message"->e.data?.let{trySend(ChatEvent.Message(it.domain()))};"typing"->trySend(ChatEvent.Typing(e.userId.orEmpty(),e.active));"online"->trySend(ChatEvent.Online(e.userId.orEmpty(),e.active));"delivered"->trySend(ChatEvent.Delivered(e.userId.orEmpty(),e.messageIds));"read"->trySend(ChatEvent.Read(e.userId.orEmpty(),e.messageIds));"error"->trySend(ChatEvent.Error(e.message?:"Realtime error"))}}};override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){sockets.remove(conversationId,webSocket);trySend(ChatEvent.Error(t.message?:"Realtime disconnected"));close(t)};override fun onClosed(webSocket:WebSocket,code:Int,reason:String){sockets.remove(conversationId,webSocket);if(code==1000||code==1008)close()else close(IOException("Realtime closed ($code)"))}});awaitClose{sockets.remove(conversationId,socket);socket.close(1000,"Screen closed")}}.retryWhen{_,attempt->delay(reconnectDelayMillis(attempt));true}
    private fun ConversationDto.domain()=Conversation(id,name,url(photo),lastMessage,lastMessageAt,unreadCount,matchId,otherUserId,onlineStatus)
    private fun MessageDto.domain()=ChatMessage(id,conversationId,senderId,body,mediaKey,replyToId,createdAt,readAt,url(mediaUrl),deliveredAt,clientMessageId)
    private fun url(value:String?)=value?.let{if(it.startsWith("http"))it else BuildConfig.API_BASE_URL.removeSuffix("/")+it}
}
