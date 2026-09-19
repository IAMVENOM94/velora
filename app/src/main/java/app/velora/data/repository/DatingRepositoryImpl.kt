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
    override suspend fun discover():DataResult<List<DiscoverProfile>> = when(val page=discoverPage()){is DataResult.Success->DataResult.Success(page.value.items,page.fromCache);is DataResult.Failure->page}
    override suspend fun discoverPage(cursor:String?):DataResult<CursorPage<DiscoverProfile>>{return when(val result=remoteCall{api.discoverPage(cursor)}){is DataResult.Success->{if(cursor==null)cache.put("discover",json.encodeToString(ListSerializer(DiscoverDto.serializer()),result.value.items));DataResult.Success(CursorPage(result.value.items.map{it.domain()},result.value.nextCursor))};is DataResult.Failure->{if(cursor!=null||!result.retryable)return result;val cached=cache.get("discover")?.let{runCatching{json.decodeFromString(ListSerializer(DiscoverDto.serializer()),it)}.getOrNull()};if(cached!=null)DataResult.Success(CursorPage(cached.map{it.domain()},null),true)else result}}}
    override suspend fun publicProfile(userId:String):DataResult<PublicProfile>{val key="public_profile_$userId";return when(val r=remoteCall{api.publicProfile(userId)}){is DataResult.Success->{cache.put(key,json.encodeToString(PublicProfileDto.serializer(),r.value));DataResult.Success(r.value.domain())};is DataResult.Failure->{if(!r.retryable)cache.remove(key);val cached=if(r.retryable)cache.get(key)?.let{runCatching{json.decodeFromString(PublicProfileDto.serializer(),it)}.getOrNull()}else null;if(cached!=null)DataResult.Success(cached.domain(),true)else r}}}
    override suspend fun like(userId:String,superLike:Boolean)=when(val r=remoteCall{api.like(userId,LikeBodyDto(if(superLike)"super_like" else "like"))}){is DataResult.Success->DataResult.Success(MatchResult(r.value.matched,r.value.matchId,r.value.conversationId,r.value.actionsRemaining,r.value.superLikesRemaining));is DataResult.Failure->r}
    override suspend fun pass(userId:String)=unit(remoteCall{api.pass(userId)})
    override suspend fun rewind()=when(val r=remoteCall{api.rewind()}){is DataResult.Success->DataResult.Success(r.value.userId);is DataResult.Failure->r}
    override suspend fun matches()=when(val r=matchesPage()){is DataResult.Success->DataResult.Success(r.value.items,r.fromCache);is DataResult.Failure->r}
    override suspend fun matchesPage(cursor:String?)=when(val r=remoteCall{api.matchesPage(cursor)}){is DataResult.Success->{if(cursor==null)cache.put("matches",json.encodeToString(ListSerializer(MatchDto.serializer()),r.value.items));DataResult.Success(CursorPage(r.value.items.map{it.domain()},r.value.nextCursor))};is DataResult.Failure->{if(cursor!=null||!r.retryable)r else{val cached=cache.get("matches")?.let{runCatching{json.decodeFromString(ListSerializer(MatchDto.serializer()),it)}.getOrNull()};if(cached!=null)DataResult.Success(CursorPage(cached.map{it.domain()},null),true)else r}}}
    override suspend fun unmatch(matchId:String)=when(val result=remoteCall{api.unmatch(matchId)}){is DataResult.Success->{cache.remove("matches");cache.remove("conversations");DataResult.Success(Unit)};is DataResult.Failure->result}
    override suspend fun preferences()=when(val r=remoteCall{api.preferences()}){is DataResult.Success->DataResult.Success(r.value.run{SearchPreferences(minAge,maxAge,maxDistanceKm,showGenders,notifications)});is DataResult.Failure->r}
    override suspend fun updatePreferences(value:SearchPreferences)=when(val r=remoteCall{api.updatePreferences(PreferenceUpdateDto(value.minAge,value.maxAge,value.maxDistanceKm,value.showGenders,value.notifications))}){is DataResult.Success->DataResult.Success(r.value.run{SearchPreferences(minAge,maxAge,maxDistanceKm,showGenders,notifications)});is DataResult.Failure->r}
    override suspend fun block(userId:String)=when(val result=remoteCall{api.block(UserIdDto(userId))}){is DataResult.Success->{cache.remove("public_profile_$userId");cache.remove("discover");cache.remove("matches");cache.remove("conversations");DataResult.Success(Unit)};is DataResult.Failure->result}
    override suspend fun blocks()=when(val r=remoteCall{api.blocks()}){is DataResult.Success->DataResult.Success(r.value.map{BlockedUser(it.blockedId,it.name,it.createdAt)});is DataResult.Failure->r}
    override suspend fun unblock(userId:String)=unit(remoteCall{api.unblock(userId)})
    override suspend fun report(userId:String,reason:String,details:String)=unit(remoteCall{api.report(ReportDto(userId,reason,details))})
    override suspend fun subscription()=when(val r=remoteCall{api.subscription()}){is DataResult.Success->DataResult.Success(r.value.run{Subscription(tier,validUntil,limits.actions,usage.actions,limits.superLikes,usage.superLikes)});is DataResult.Failure->r}
    override suspend fun activateDevelopmentPremium():DataResult<Subscription>{val activated=remoteCall{api.activateDevelopmentPremium()};return if(activated is DataResult.Failure)activated else subscription()}
    override suspend fun boost()=when(val r=remoteCall{api.boost()}){is DataResult.Success->DataResult.Success(r.value.boostedUntil);is DataResult.Failure->r}
    override suspend fun receivedLikes()=receivedLikesPage()
    override suspend fun receivedLikesPage(cursor:String?)=when(val r=remoteCall{api.receivedLikesPage(cursor)}){is DataResult.Success->DataResult.Success(ReceivedLikes(r.value.count,r.value.locked,r.value.items.map{ReceivedLike(it.userId,it.name,it.age,it.kind,url(it.photo))},r.value.nextCursor));is DataResult.Failure->r}
    override suspend fun privacy()=when(val r=remoteCall{api.privacy()}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}
    override suspend fun updatePrivacy(value:PrivacySettings)=when(val r=remoteCall{api.updatePrivacy(value.dto())}){is DataResult.Success->DataResult.Success(r.value.domain());is DataResult.Failure->r}
    private fun DiscoverDto.domain()=DiscoverProfile(userId,name,age,city.orEmpty(),bio,datingGoal,distanceKm,url(photo),onlineStatus=="ONLINE",interests,photos.mapNotNull(::url),gender,verified,onlineStatus)
    private fun MatchDto.domain()=MatchSummary(id,name,url(photo),createdAt,userId,conversationId,onlineStatus)
    private fun PublicProfileDto.domain()=PublicProfile(userId,displayName,age,gender,bio,relationshipGoal,city,distanceKm,verified,onlineStatus,lastSeen,relationshipState,matchId,conversationId,canMessage,photos.map{PublicProfilePhoto(it.id,it.position,url(it.thumbnailUrl)!!,url(it.mediumUrl)!!,url(it.fullUrl)!!,it.width,it.height)},interests,socialProfiles.map{SocialProfile(it.provider,it.username,it.profileUrl)})
    private fun PrivacyDto.domain()=PrivacySettings(showOnlineStatus,showDistance,showCity,discoverableProfile,readReceipts,pushNotifications,hideProfileTemporarily)
    private fun PrivacySettings.dto()=PrivacyDto(showOnlineStatus,showDistance,showCity,discoverableProfile,readReceipts,pushNotifications,hideProfileTemporarily)
    private fun url(value:String?)=value?.let{if(it.startsWith("http"))it else BuildConfig.API_BASE_URL.removeSuffix("/")+it}
    private fun unit(value:DataResult<*>)=when(value){is DataResult.Success->DataResult.Success(Unit);is DataResult.Failure->value}
}
