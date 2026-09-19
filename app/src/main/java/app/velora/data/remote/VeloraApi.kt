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
    @GET("auth/providers") suspend fun providers():ProviderAvailabilityDto
    @POST("auth/google") suspend fun google(@Body value:GoogleCredentialDto):TokenPairDto
    @POST("auth/vk/sdk") suspend fun vk(@Body value:VkCredentialDto):TokenPairDto
    @POST("auth/phone/request") suspend fun requestPhone(@Body value:PhoneRequestDto):PhoneRequestResultDto
    @POST("auth/phone/verify") suspend fun verifyPhone(@Body value:PhoneVerifyDto):UnifiedAuthResultDto
    @GET("auth/{provider}/start") suspend fun oauthStart(@Path("provider")provider:String,@Query("mode")mode:String):OAuthStartDto
    @POST("auth/{provider}/callback") suspend fun oauthCallback(@Path("provider")provider:String,@Body value:OAuthCallbackDto):UnifiedAuthResultDto
    @GET("users/me") suspend fun me():MeDto
    @GET("users/{id}/profile") suspend fun publicProfile(@Path("id")id:String):PublicProfileDto
    @PATCH("users/me/profile") suspend fun profile(@Body value:ProfileDraftDto):JsonObject
    @PUT("users/me/onboarding") suspend fun onboarding(@Body value:OnboardingDto):OkDto
    @PUT("users/me/interests") suspend fun updateInterests(@Body value:InterestNamesDto):List<String>
    @PATCH("users/me/visibility") suspend fun visibility(@Body value:VisibilityDto):Map<String,Boolean>
    @GET("users/me/identities") suspend fun identities():List<AuthIdentityDto>
    @POST("users/me/identities/link/google") suspend fun linkGoogle(@Body value:GoogleCredentialDto):List<AuthIdentityDto>
    @POST("users/me/identities/link/vk") suspend fun linkVk(@Body value:VkCredentialDto):List<AuthIdentityDto>
    @DELETE("users/me/identities/{provider}") suspend fun unlinkIdentity(@Path("provider")provider:String):OkDto
    @PUT("users/me/locale") suspend fun locale(@Body value:LocaleDto):LocaleDto
    @GET("users/me/location") suspend fun location():ProfileLocationDto?
    @PUT("users/me/location") suspend fun updateLocation(@Body value:LocationUpdateDto):ProfileLocationDto
    @GET("users/me/social-profiles") suspend fun socialProfiles():List<SocialProfileDto>
    @POST("users/me/social-profiles") suspend fun addSocialProfile(@Body value:SocialProfileUpdateDto):SocialProfileDto
    @DELETE("users/me/social-profiles/{provider}") suspend fun deleteSocialProfile(@Path("provider")provider:String):OkDto
    @DELETE("users/me") suspend fun deleteAccount():OkDto
    @POST("users/me/photos/upload-url") suspend fun photoUploadUrl(@Body value:UploadUrlDto):UploadTicketDto
    @GET("users/me/photos") suspend fun photos():List<PhotoResultDto>
    @Multipart @POST("users/me/photos/{position}/upload") suspend fun uploadPhoto(@Path("position")position:Int,@Part file:MultipartBody.Part):PhotoResultDto
    @DELETE("users/me/photos/{id}") suspend fun deletePhoto(@Path("id")id:String):OkDto
    @PUT("users/me/photos/order") suspend fun reorderPhotos(@Body value:PhotoOrderDto):List<PhotoResultDto>
    @PUT("users/me/photos/{id}/main") suspend fun makePhotoMain(@Path("id")id:String):List<PhotoResultDto>
    @GET("discover") suspend fun discover():List<DiscoverDto>
    @GET("feed/discover") suspend fun discoverPage(@Query("cursor")cursor:String?=null,@Query("limit")limit:Int=20):DiscoverPageDto
    @POST("likes/{id}") suspend fun like(@Path("id")id:String,@Body value:LikeBodyDto):LikeResultDto
    @POST("passes/{id}") suspend fun pass(@Path("id")id:String):OkDto
    @POST("discover/rewind") suspend fun rewind():RewindDto
    @GET("likes") suspend fun receivedLikes():ReceivedLikesDto
    @GET("feed/likes") suspend fun receivedLikesPage(@Query("cursor")cursor:String?=null,@Query("limit")limit:Int=30):ReceivedLikesPageDto
    @GET("matches") suspend fun matches():List<MatchDto>
    @GET("feed/matches") suspend fun matchesPage(@Query("cursor")cursor:String?=null,@Query("limit")limit:Int=30):MatchPageDto
    @DELETE("matches/{id}") suspend fun unmatch(@Path("id")id:String):OkDto
    @GET("conversations") suspend fun conversations():List<ConversationDto>
    @GET("feed/conversations") suspend fun conversationsPage(@Query("cursor")cursor:String?=null,@Query("limit")limit:Int=30):ConversationPageDto
    @GET("conversations/{id}/messages") suspend fun messages(@Path("id")id:String):List<MessageDto>
    @GET("feed/conversations/{id}/messages") suspend fun messagesPage(@Path("id")id:String,@Query("cursor")cursor:String?=null,@Query("limit")limit:Int=50):MessagePageDto
    @POST("conversations/{id}/messages") suspend fun send(@Path("id")id:String,@Body value:SendMessageDto):MessageDto
    @Multipart @POST("conversations/{id}/messages/photo") suspend fun sendPhoto(@Path("id")id:String,@Query("replyToId")replyToId:String?=null,@Query("clientMessageId")clientMessageId:String?=null,@Part file:MultipartBody.Part):MessageDto
    @PATCH("conversations/{id}/read") suspend fun read(@Path("id")id:String,@Body value:Map<String,String> = emptyMap()):OkDto
    @GET("preferences") suspend fun preferences():PreferenceDto
    @PATCH("preferences") suspend fun updatePreferences(@Body value:PreferenceUpdateDto):PreferenceDto
    @GET("privacy") suspend fun privacy():PrivacyDto
    @PATCH("privacy") suspend fun updatePrivacy(@Body value:PrivacyDto):PrivacyDto
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
