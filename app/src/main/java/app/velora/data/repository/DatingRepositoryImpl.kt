package app.velora.data.repository

import app.velora.BuildConfig
import app.velora.data.local.ResponseCache
import app.velora.data.remote.*
import app.velora.domain.model.*
import app.velora.domain.repository.DatingRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class DatingRepositoryImpl @Inject constructor(private val api:VeloraApi,private val cache:ResponseCache,private val json:Json):DatingRepository{
    override suspend fun discover():DataResult<List<DiscoverProfile>>{return when(val result=remoteCall{api.discover()}){is DataResult.Success->{cache.put("discover",json.encodeToString(ListSerializer(DiscoverDto.serializer()),result.value));DataResult.Success(result.value.map{it.domain()})};is DataResult.Failure->{val cached=cache.get("discover")?.let{runCatching{json.decodeFromString(ListSerializer(DiscoverDto.serializer()),it)}.getOrNull()};if(cached!=null)DataResult.Success(cached.map{it.domain()},true)else result}}}
    override suspend fun like(userId:String,superLike:Boolean)=when(val r=remoteCall{api.like(userId,LikeBodyDto(if(superLike)"super_like" else "like"))}){is DataResult.Success->DataResult.Success(MatchResult(r.value.matched,r.value.matchId,r.value.conversationId,r.value.actionsRemaining,r.value.superLikesRemaining));is DataResult.Failure->r}
    override suspend fun pass(userId:String)=unit(remoteCall{api.pass(userId)})
    override suspend fun rewind()=when(val r=remoteCall{api.rewind()}){is DataResult.Success->DataResult.Success(r.value.userId);is DataResult.Failure->r}
    override suspend fun matches()=when(val r=remoteCall{api.matches()}){is DataResult.Success->DataResult.Success(r.value.map{MatchSummary(it.id,it.name,url(it.photo),it.createdAt)});is DataResult.Failure->r}
    override suspend fun unmatch(matchId:String)=unit(remoteCall{api.unmatch(matchId)})
    override suspend fun preferences()=when(val r=remoteCall{api.preferences()}){is DataResult.Success->DataResult.Success(r.value.run{SearchPreferences(minAge,maxAge,maxDistanceKm,showGenders,notifications)});is DataResult.Failure->r}
    override suspend fun updatePreferences(value:SearchPreferences)=when(val r=remoteCall{api.updatePreferences(PreferenceUpdateDto(value.minAge,value.maxAge,value.maxDistanceKm,value.showGenders,value.notifications))}){is DataResult.Success->DataResult.Success(r.value.run{SearchPreferences(minAge,maxAge,maxDistanceKm,showGenders,notifications)});is DataResult.Failure->r}
    override suspend fun block(userId:String)=unit(remoteCall{api.block(UserIdDto(userId))})
    override suspend fun blocks()=when(val r=remoteCall{api.blocks()}){is DataResult.Success->DataResult.Success(r.value.map{BlockedUser(it.blockedId,it.name,it.createdAt)});is DataResult.Failure->r}
    override suspend fun unblock(userId:String)=unit(remoteCall{api.unblock(userId)})
    override suspend fun report(userId:String,reason:String,details:String)=unit(remoteCall{api.report(ReportDto(userId,reason,details))})
    override suspend fun subscription()=when(val r=remoteCall{api.subscription()}){is DataResult.Success->DataResult.Success(r.value.run{Subscription(tier,validUntil,limits.actions,usage.actions,limits.superLikes,usage.superLikes)});is DataResult.Failure->r}
    override suspend fun activateDevelopmentPremium():DataResult<Subscription>{val activated=remoteCall{api.activateDevelopmentPremium()};return if(activated is DataResult.Failure)activated else subscription()}
    override suspend fun boost()=when(val r=remoteCall{api.boost()}){is DataResult.Success->DataResult.Success(r.value.boostedUntil);is DataResult.Failure->r}
    override suspend fun receivedLikes()=when(val r=remoteCall{api.receivedLikes()}){is DataResult.Success->DataResult.Success(ReceivedLikes(r.value.count,r.value.locked,r.value.people.map{ReceivedLike(it.userId,it.name,it.age,it.kind,url(it.photo))}));is DataResult.Failure->r}
    private fun DiscoverDto.domain()=DiscoverProfile(userId,name,age,city,bio,datingGoal,distanceKm,url(photo),online,interests,photos.mapNotNull(::url))
    private fun url(value:String?)=value?.let{if(it.startsWith("http"))it else BuildConfig.API_BASE_URL.removeSuffix("/")+it}
    private fun unit(value:DataResult<*>)=when(value){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->value}
}
