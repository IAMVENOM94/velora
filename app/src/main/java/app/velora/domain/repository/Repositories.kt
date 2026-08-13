package app.velora.domain.repository

import app.velora.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val hasSession:Flow<Boolean>
    suspend fun register(email:String,password:String):DataResult<Unit>
    suspend fun login(email:String,password:String):DataResult<Unit>
    suspend fun recover(email:String):DataResult<RecoveryRequest>
    suspend fun resetPassword(token:String,password:String):DataResult<Unit>
    suspend fun logout()
}
interface ProfileRepository {
    suspend fun me():DataResult<MyProfile>
    suspend fun completeOnboarding(profile:ProfileDraft,interests:List<String>,showGenders:List<String>):DataResult<MyProfile>
    suspend fun saveProfile(profile:ProfileDraft):DataResult<MyProfile>
    suspend fun saveInterests(interests:List<String>):DataResult<List<String>>
    suspend fun setHidden(hidden:Boolean):DataResult<Unit>
    suspend fun deleteAccount():DataResult<Unit>
}
interface PhotoRepository {
    suspend fun list():DataResult<List<ProfilePhoto>>
    suspend fun upload(position:Int,bytes:ByteArray,mimeType:String):DataResult<ProfilePhoto>
    suspend fun delete(id:String):DataResult<Unit>
}
interface DatingRepository {
    suspend fun discover():DataResult<List<DiscoverProfile>>
    suspend fun like(userId:String,superLike:Boolean=false):DataResult<MatchResult>
    suspend fun pass(userId:String):DataResult<Unit>
    suspend fun rewind():DataResult<String?>
    suspend fun matches():DataResult<List<MatchSummary>>
    suspend fun unmatch(matchId:String):DataResult<Unit>
    suspend fun preferences():DataResult<SearchPreferences>
    suspend fun updatePreferences(value:SearchPreferences):DataResult<SearchPreferences>
    suspend fun block(userId:String):DataResult<Unit>
    suspend fun blocks():DataResult<List<BlockedUser>>
    suspend fun unblock(userId:String):DataResult<Unit>
    suspend fun report(userId:String,reason:String,details:String):DataResult<Unit>
    suspend fun subscription():DataResult<Subscription>
    suspend fun activateDevelopmentPremium():DataResult<Subscription>
    suspend fun boost():DataResult<String>
    suspend fun receivedLikes():DataResult<ReceivedLikes>
}
interface ChatRepository {
    suspend fun conversations():DataResult<List<Conversation>>
    suspend fun messages(conversationId:String):DataResult<List<ChatMessage>>
    suspend fun send(conversationId:String,body:String,replyToId:String?=null):DataResult<ChatMessage>
    suspend fun sendPhoto(conversationId:String,bytes:ByteArray,mimeType:String,replyToId:String?=null):DataResult<ChatMessage>
    suspend fun markRead(conversationId:String):DataResult<Unit>
    fun sendTyping(conversationId:String,active:Boolean)
    fun realtime(conversationId:String):Flow<ChatEvent>
}
sealed interface ChatEvent { data class Message(val value:ChatMessage):ChatEvent; data class Typing(val userId:String,val active:Boolean):ChatEvent; data class Online(val userId:String,val active:Boolean):ChatEvent; data class Read(val userId:String,val messageIds:List<String>):ChatEvent; data object Connected:ChatEvent; data class Error(val message:String):ChatEvent }
