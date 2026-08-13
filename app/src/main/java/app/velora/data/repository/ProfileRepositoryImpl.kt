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
    override suspend fun deleteAccount()=when(val r=remoteCall{api.deleteAccount()}){is DataResult.Success->{tokens.clear();DataResult.Success(Unit)};is DataResult.Failure->r}
    private fun MeDto.toDomain()=MyProfile(id,email,name,onboardingComplete==true,birthDate?.take(10),gender,city,bio,datingGoal,photo?.let{if(it.startsWith("http"))it else app.velora.BuildConfig.API_BASE_URL.removeSuffix("/")+it},interests,hidden)
}
