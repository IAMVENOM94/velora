package app.velora.data.repository

import app.velora.data.local.TokenStore
import app.velora.data.remote.*
import app.velora.domain.model.*
import app.velora.domain.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class ProfileRepositoryImpl @Inject constructor(private val api:VeloraApi,private val tokens:TokenStore):ProfileRepository{
    override suspend fun me()=remoteCall{api.me().toDomain()}
    override suspend fun completeOnboarding(profile:ProfileDraft,interests:List<String>,showGenders:List<String>):DataResult<MyProfile>{val saved=remoteCall{api.onboarding(OnboardingDto(profile.name,profile.birthDate,profile.gender,profile.city,profile.bio,profile.datingGoal,interests,showGenders))};return if(saved is DataResult.Failure)saved else me()}
    override suspend fun saveProfile(profile:ProfileDraft):DataResult<MyProfile>{val saved=remoteCall{api.profile(ProfileDraftDto(profile.name,profile.birthDate,profile.gender,profile.city,profile.bio,profile.datingGoal))};return if(saved is DataResult.Failure)saved else me()}
    override suspend fun saveInterests(interests:List<String>)=remoteCall{api.updateInterests(InterestNamesDto(interests))}
    override suspend fun setHidden(hidden:Boolean)=when(val r=remoteCall{api.visibility(VisibilityDto(hidden))}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    override suspend fun identities()=when(val r=remoteCall{api.identities()}){is DataResult.Success->DataResult.Success(r.value.map{it.toDomain()});is DataResult.Failure->r}
    override suspend fun linkGoogle(idToken:String)=when(val r=remoteCall{api.linkGoogle(GoogleCredentialDto(idToken))}){is DataResult.Success->DataResult.Success(r.value.map{it.toDomain()});is DataResult.Failure->r}
    override suspend fun linkVk(accessToken:String)=when(val r=remoteCall{api.linkVk(VkCredentialDto(accessToken))}){is DataResult.Success->DataResult.Success(r.value.map{it.toDomain()});is DataResult.Failure->r}
    override suspend fun unlinkIdentity(provider:String)=when(val r=remoteCall{api.unlinkIdentity(provider)}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    override suspend fun saveLocale(preferredLocale:String?)=when(val r=remoteCall{api.locale(LocaleDto(preferredLocale))}){is DataResult.Success->DataResult.Success(r.value.preferredLocale);is DataResult.Failure->r}
    override suspend fun location()=when(val r=remoteCall{api.location()}){is DataResult.Success->DataResult.Success(r.value?.toDomain());is DataResult.Failure->r}
    override suspend fun saveLocation(location:ProfileLocation)=when(val r=remoteCall{api.updateLocation(LocationUpdateDto(location.country,location.region,location.city,location.latitude,location.longitude,location.locationSource,location.accuracyMeters))}){is DataResult.Success->DataResult.Success(r.value.toDomain());is DataResult.Failure->r}
    override suspend fun socialProfiles()=when(val r=remoteCall{api.socialProfiles()}){is DataResult.Success->DataResult.Success(r.value.map{it.toDomain()});is DataResult.Failure->r}
    override suspend fun saveInstagram(username:String,visible:Boolean)=when(val r=remoteCall{api.addSocialProfile(SocialProfileUpdateDto(username=username,visibleInProfile=visible))}){is DataResult.Success->DataResult.Success(r.value.toDomain());is DataResult.Failure->r}
    override suspend fun removeInstagram()=when(val r=remoteCall{api.deleteSocialProfile("instagram")}){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->r}
    override suspend fun deleteAccount()=when(val r=remoteCall{api.deleteAccount()}){is DataResult.Success->{tokens.clear();DataResult.Success(Unit)};is DataResult.Failure->r}
    private fun MeDto.toDomain()=MyProfile(id,email,name,onboardingComplete==true,birthDate?.take(10),gender,city,bio,datingGoal,photo?.let{if(it.startsWith("http"))it else app.velora.BuildConfig.API_BASE_URL.removeSuffix("/")+it},interests,hidden)
    private fun AuthIdentityDto.toDomain()=AuthIdentity(id,provider,email,phone,emailVerified,phoneVerified,createdAt,lastUsedAt)
    private fun ProfileLocationDto.toDomain()=ProfileLocation(country,region,city,latitude,longitude,locationSource,accuracyMeters,updatedAt)
    private fun SocialProfileDto.toDomain()=SocialProfile(provider,username,profileUrl,verified,visibleInProfile,createdAt,updatedAt)
}
