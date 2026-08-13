package app.velora.data.remote

import okhttp3.MultipartBody
import retrofit2.http.*
import kotlinx.serialization.json.JsonObject

interface VeloraApi {
    @POST("auth/register") suspend fun register(@Body value:CredentialsDto):TokenPairDto
    @POST("auth/login") suspend fun login(@Body value:CredentialsDto):TokenPairDto
    @POST("auth/refresh") suspend fun refresh(@Body value:RefreshDto):TokenPairDto
    @POST("auth/logout") suspend fun logout(@Body value:RefreshDto):OkDto
    @POST("auth/recover") suspend fun recover(@Body value:RecoverDto):RecoverResultDto
    @POST("auth/reset") suspend fun resetPassword(@Body value:ResetPasswordDto):OkDto
    @GET("users/me") suspend fun me():MeDto
    @PATCH("users/me/profile") suspend fun profile(@Body value:ProfileDraftDto):JsonObject
    @PUT("users/me/onboarding") suspend fun onboarding(@Body value:OnboardingDto):OkDto
    @PUT("users/me/interests") suspend fun updateInterests(@Body value:InterestNamesDto):List<String>
    @PATCH("users/me/visibility") suspend fun visibility(@Body value:VisibilityDto):Map<String,Boolean>
    @DELETE("users/me") suspend fun deleteAccount():OkDto
    @POST("users/me/photos/upload-url") suspend fun photoUploadUrl(@Body value:UploadUrlDto):UploadTicketDto
    @GET("users/me/photos") suspend fun photos():List<PhotoResultDto>
    @Multipart @POST("users/me/photos/{position}/upload") suspend fun uploadPhoto(@Path("position")position:Int,@Part file:MultipartBody.Part):PhotoResultDto
    @DELETE("users/me/photos/{id}") suspend fun deletePhoto(@Path("id")id:String):OkDto
    @GET("discover") suspend fun discover():List<DiscoverDto>
    @POST("likes/{id}") suspend fun like(@Path("id")id:String,@Body value:LikeBodyDto):LikeResultDto
    @POST("passes/{id}") suspend fun pass(@Path("id")id:String):OkDto
    @POST("discover/rewind") suspend fun rewind():RewindDto
    @GET("likes") suspend fun receivedLikes():ReceivedLikesDto
    @GET("matches") suspend fun matches():List<MatchDto>
    @DELETE("matches/{id}") suspend fun unmatch(@Path("id")id:String):OkDto
    @GET("conversations") suspend fun conversations():List<ConversationDto>
    @GET("conversations/{id}/messages") suspend fun messages(@Path("id")id:String):List<MessageDto>
    @POST("conversations/{id}/messages") suspend fun send(@Path("id")id:String,@Body value:SendMessageDto):MessageDto
    @Multipart @POST("conversations/{id}/messages/photo") suspend fun sendPhoto(@Path("id")id:String,@Query("replyToId")replyToId:String?=null,@Part file:MultipartBody.Part):MessageDto
    @PATCH("conversations/{id}/read") suspend fun read(@Path("id")id:String,@Body value:Map<String,String> = emptyMap()):OkDto
    @GET("preferences") suspend fun preferences():PreferenceDto
    @PATCH("preferences") suspend fun updatePreferences(@Body value:PreferenceUpdateDto):PreferenceDto
    @POST("blocks") suspend fun block(@Body value:UserIdDto):OkDto
    @GET("blocks") suspend fun blocks():List<BlockedUserDto>
    @DELETE("blocks/{id}") suspend fun unblock(@Path("id")id:String):OkDto
    @POST("reports") suspend fun report(@Body value:ReportDto):OkDto
    @GET("subscription") suspend fun subscription():SubscriptionDto
    @POST("subscription/development/activate-premium") suspend fun activateDevelopmentPremium():SubscriptionDto
    @POST("subscription/boost") suspend fun boost():BoostDto
    @POST("notifications/devices") suspend fun registerDevice(@Body value:DeviceDto):OkDto
    @HTTP(method="DELETE",path="notifications/devices",hasBody=true) suspend fun removeDevice(@Body value:DeviceDto):OkDto
}
