package app.velora.domain.repository

import app.velora.domain.model.*
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val hasSession:Flow<Boolean>
    suspend fun register(email:String,password:String):DataResult<Unit>
    suspend fun login(email:String,password:String):DataResult<Unit>
    suspend fun recover(email:String):DataResult<RecoveryRequest>
    suspend fun resetPassword(token:String,password:String):DataResult<Unit>
    suspend fun providers():DataResult<AuthProviders>
    suspend fun google(idToken:String):DataResult<Unit>
    suspend fun vk(accessToken:String):DataResult<Unit>
    suspend fun requestPhone(phone:String,deviceId:String):DataResult<PhoneOtpChallenge>
    suspend fun verifyPhone(phone:String,code:String,link:Boolean=false):DataResult<List<AuthIdentity>?>
    suspend fun oauthStart(provider:String,link:Boolean=false):DataResult<OAuthAuthorization>
    suspend fun oauthComplete(provider:String,code:String,state:String,deviceId:String?=null):DataResult<List<AuthIdentity>?>
    suspend fun logout()
}
interface ProfileRepository {
    suspend fun me():DataResult<MyProfile>
    suspend fun completeOnboarding(profile:ProfileDraft,interests:List<String>,showGenders:List<String>):DataResult<MyProfile>
    suspend fun saveProfile(profile:ProfileDraft):DataResult<MyProfile>
    suspend fun saveInterests(interests:List<String>):DataResult<List<String>>
    suspend fun setHidden(hidden:Boolean):DataResult<Unit>
    suspend fun identities():DataResult<List<AuthIdentity>>
    suspend fun linkGoogle(idToken:String):DataResult<List<AuthIdentity>>
    suspend fun linkVk(accessToken:String):DataResult<List<AuthIdentity>>
    suspend fun unlinkIdentity(provider:String):DataResult<Unit>
    suspend fun saveLocale(preferredLocale:String?):DataResult<String?>
    suspend fun location():DataResult<ProfileLocation?>
    suspend fun saveLocation(location:ProfileLocation):DataResult<ProfileLocation>
    suspend fun socialProfiles():DataResult<List<SocialProfile>>
    suspend fun saveInstagram(username:String,visible:Boolean=true):DataResult<SocialProfile>
    suspend fun removeInstagram():DataResult<Unit>
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
