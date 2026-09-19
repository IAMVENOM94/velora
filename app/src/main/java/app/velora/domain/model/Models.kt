package app.velora.domain.model

data class Session(val accessToken:String,val refreshToken:String)
data class MyProfile(val id:String,val email:String?,val name:String?,val onboardingComplete:Boolean,val birthDate:String?=null,val gender:String?=null,val city:String?=null,val bio:String?=null,val datingGoal:String?=null,val photoUrl:String?=null,val interests:List<String> = emptyList(),val hidden:Boolean=false)
data class ProfilePhoto(val id:String,val position:Int,val url:String,val thumbnailUrl:String=url,val mediumUrl:String=url,val fullUrl:String=url,val width:Int?=null,val height:Int?=null)
data class RecoveryRequest(val accepted:Boolean,val developmentToken:String?=null)
data class DiscoverProfile(val id:String,val name:String,val age:Int,val city:String,val bio:String,val goal:String,val distanceKm:Int?,val photoUrl:String?,val online:Boolean,val interests:List<String> = emptyList(),val photoUrls:List<String> = emptyList(),val gender:String="other",val verified:Boolean=false,val onlineStatus:String=if(online)"ONLINE" else "RECENTLY_ONLINE")
data class MatchResult(val matched:Boolean,val matchId:String?=null,val conversationId:String?=null,val actionsRemaining:Int?=null,val superLikesRemaining:Int?=null)
data class MatchSummary(val id:String,val name:String,val photoUrl:String?,val createdAt:String,val userId:String=id,val conversationId:String?=null,val onlineStatus:String="OFFLINE")
data class Conversation(val id:String,val name:String,val photoUrl:String?,val lastMessage:String?,val lastMessageAt:String?,val unreadCount:Int,val matchId:String?=null,val otherUserId:String?=null,val onlineStatus:String="OFFLINE")
data class ChatMessage(val id:String,val conversationId:String,val senderId:String,val body:String?,val mediaKey:String?,val replyToId:String?,val createdAt:String,val readAt:String?,val mediaUrl:String?=null,val deliveredAt:String?=null,val clientMessageId:String?=null)
data class SearchPreferences(val minAge:Int=21,val maxAge:Int=40,val maxDistanceKm:Int=50,val showGenders:List<String> = listOf("woman"),val notifications:Boolean=true)
data class ProfileDraft(val name:String,val birthDate:String,val gender:String,val city:String,val bio:String,val datingGoal:String)
data class BlockedUser(val id:String,val name:String?,val createdAt:String)
data class Subscription(val tier:String="free",val validUntil:String?=null,val actionLimit:Int=20,val actionsUsed:Int=0,val superLikeLimit:Int=1,val superLikesUsed:Int=0){val premium:Boolean get()=tier=="premium"}
data class ReceivedLike(val id:String,val name:String,val age:Int,val kind:String,val photoUrl:String?)
data class ReceivedLikes(val count:Int=0,val locked:Boolean=true,val people:List<ReceivedLike> = emptyList(),val nextCursor:String?=null)
data class AuthProviders(val email:Boolean=true,val phone:Boolean=false,val google:Boolean=false,val x:Boolean=false,val vk:Boolean=false,val instagram:Boolean=true)
data class AuthIdentity(val id:String,val provider:String,val maskedEmail:String?=null,val maskedPhone:String?=null,val emailVerified:Boolean=false,val phoneVerified:Boolean=false,val createdAt:String="",val lastUsedAt:String="")
data class PhoneOtpChallenge(val phone:String,val expiresInSeconds:Int,val resendAfterSeconds:Int,val developmentCode:String?=null)
data class OAuthAuthorization(val provider:String,val authorizationUrl:String,val state:String)
data class ProfileLocation(val country:String?=null,val region:String?=null,val city:String,val latitude:Double?=null,val longitude:Double?=null,val locationSource:String,val accuracyMeters:Int?=null,val updatedAt:String?=null)
data class SocialProfile(val provider:String,val username:String,val profileUrl:String,val verified:Boolean=false,val visibleInProfile:Boolean=false,val createdAt:String="",val updatedAt:String="")
data class PublicProfilePhoto(val id:String,val position:Int,val thumbnailUrl:String,val mediumUrl:String,val fullUrl:String,val width:Int,val height:Int)
data class PublicProfile(val userId:String,val displayName:String,val age:Int,val gender:String,val bio:String,val relationshipGoal:String,val city:String?,val distanceKm:Int?,val verified:Boolean,val onlineStatus:String,val lastSeen:String?,val relationshipState:String,val matchId:String?,val conversationId:String?,val canMessage:Boolean,val photos:List<PublicProfilePhoto>,val interests:List<String>,val socialProfiles:List<SocialProfile> = emptyList())
data class PrivacySettings(val showOnlineStatus:Boolean=true,val showDistance:Boolean=true,val showCity:Boolean=true,val discoverableProfile:Boolean=true,val readReceipts:Boolean=true,val pushNotifications:Boolean=true,val hideProfileTemporarily:Boolean=false)
data class CursorPage<T>(val items:List<T>,val nextCursor:String?)

sealed interface DataResult<out T>{data class Success<T>(val value:T,val fromCache:Boolean=false):DataResult<T>;data class Failure(val message:String,val retryable:Boolean=true):DataResult<Nothing>}
