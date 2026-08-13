package app.velora.domain.model

data class Session(val accessToken:String,val refreshToken:String)
data class MyProfile(val id:String,val email:String,val name:String?,val onboardingComplete:Boolean,val birthDate:String?=null,val gender:String?=null,val city:String?=null,val bio:String?=null,val datingGoal:String?=null,val photoUrl:String?=null,val interests:List<String> = emptyList(),val hidden:Boolean=false)
data class ProfilePhoto(val id:String,val position:Int,val url:String)
data class RecoveryRequest(val accepted:Boolean,val developmentToken:String?=null)
data class DiscoverProfile(val id:String,val name:String,val age:Int,val city:String,val bio:String,val goal:String,val distanceKm:Int?,val photoUrl:String?,val online:Boolean,val interests:List<String> = emptyList(),val photoUrls:List<String> = emptyList())
data class MatchResult(val matched:Boolean,val matchId:String?=null,val conversationId:String?=null,val actionsRemaining:Int?=null,val superLikesRemaining:Int?=null)
data class MatchSummary(val id:String,val name:String,val photoUrl:String?,val createdAt:String)
data class Conversation(val id:String,val name:String,val photoUrl:String?,val lastMessage:String?,val lastMessageAt:String?,val unreadCount:Int,val matchId:String?=null,val otherUserId:String?=null)
data class ChatMessage(val id:String,val conversationId:String,val senderId:String,val body:String?,val mediaKey:String?,val replyToId:String?,val createdAt:String,val readAt:String?,val mediaUrl:String?=null)
data class SearchPreferences(val minAge:Int=21,val maxAge:Int=40,val maxDistanceKm:Int=50,val showGenders:List<String> = listOf("woman"),val notifications:Boolean=true)
data class ProfileDraft(val name:String,val birthDate:String,val gender:String,val city:String,val bio:String,val datingGoal:String)
data class BlockedUser(val id:String,val name:String?,val createdAt:String)
data class Subscription(val tier:String="free",val validUntil:String?=null,val actionLimit:Int=20,val actionsUsed:Int=0,val superLikeLimit:Int=1,val superLikesUsed:Int=0){val premium:Boolean get()=tier=="premium"}
data class ReceivedLike(val id:String,val name:String,val age:Int,val kind:String,val photoUrl:String?)
data class ReceivedLikes(val count:Int=0,val locked:Boolean=true,val people:List<ReceivedLike> = emptyList())

sealed interface DataResult<out T>{data class Success<T>(val value:T,val fromCache:Boolean=false):DataResult<T>;data class Failure(val message:String,val retryable:Boolean=true):DataResult<Nothing>}
