package app.velora.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.velora.data.local.TokenStore
import app.velora.domain.model.*
import app.velora.domain.repository.*
import app.velora.domain.notification.PushRegistration
import app.velora.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SessionPhase { Checking, SignedOut, NeedsOnboarding, Ready }
data class AppUiState(
    val phase:SessionPhase=SessionPhase.Checking,
    val busy:Boolean=false,
    val error:String?=null,
    val offline:Boolean=false,
    val me:MyProfile?=null,
    val profilePhotos:List<ProfilePhoto> = emptyList(),
    val discover:List<DiscoverProfile> = emptyList(),
    val matches:List<MatchSummary> = emptyList(),
    val conversations:List<Conversation> = emptyList(),
    val activeConversationId:String?=null,
    val messages:List<ChatMessage> = emptyList(),
    val preferences:SearchPreferences=SearchPreferences(),
    val pendingMatch:MatchResult?=null,
    val matchedProfile:DiscoverProfile?=null,
    val typing:Boolean=false,
    val peerOnline:Boolean=false,
    val homeLoaded:Boolean=false,
    val blockedUsers:List<BlockedUser> = emptyList(),
    val subscription:Subscription=Subscription(),
    val receivedLikes:ReceivedLikes=ReceivedLikes(),
    val recoveryToken:String?=null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tokenStore:TokenStore,
    private val loginUseCase:LoginUseCase,
    private val registerUseCase:RegisterUseCase,
    private val onboardingUseCase:CompleteOnboardingUseCase,
    private val likeUseCase:LikeProfileUseCase,
    private val sendMessageUseCase:SendMessageUseCase,
    private val auth:AuthRepository,
    private val profile:ProfileRepository,
    private val dating:DatingRepository,
    private val chat:ChatRepository,
    private val photos:PhotoRepository,
    private val pushRegistration:PushRegistration,
):ViewModel(){
    private val _state=MutableStateFlow(AppUiState())
    val state:StateFlow<AppUiState> = _state.asStateFlow()
    private var realtimeJob:Job?=null
    private var typingJob:Job?=null
    private var typingActive=false

    init{viewModelScope.launch{tokenStore.hasSession.distinctUntilChanged().collectLatest{hasSession->if(!hasSession)_state.value=AppUiState(phase=SessionPhase.SignedOut) else bootstrap()}}}

    private suspend fun bootstrap(){_state.update{it.copy(phase=SessionPhase.Checking,error=null)};when(val result=profile.me()){is DataResult.Success->{val phase=if(result.value.onboardingComplete)SessionPhase.Ready else SessionPhase.NeedsOnboarding;_state.update{it.copy(phase=phase,me=result.value)};if(phase==SessionPhase.Ready){loadHome();pushRegistration.register()}};is DataResult.Failure->{if(result.message.contains("expired",true)){auth.logout();_state.value=AppUiState(phase=SessionPhase.SignedOut,error=result.message)}else{_state.update{it.copy(phase=SessionPhase.Ready,offline=true,error=result.message)};loadHome()}}}}
    fun login(email:String,password:String)=task{when(val r=loginUseCase(email,password)){is DataResult.Success->Unit;is DataResult.Failure->fail(r)}}
    fun register(email:String,password:String)=task{when(val r=registerUseCase(email,password)){is DataResult.Success->_state.update{it.copy(phase=SessionPhase.NeedsOnboarding)};is DataResult.Failure->fail(r)}}
    fun completeOnboarding(value:ProfileDraft,interests:List<String>,showGenders:List<String>)=task{when(val r=onboardingUseCase(value,interests,showGenders)){is DataResult.Success->{_state.update{it.copy(phase=SessionPhase.Ready,me=r.value)};loadHome();pushRegistration.register()};is DataResult.Failure->fail(r)}}
    fun saveProfile(value:ProfileDraft,interests:List<String>)=task{when(val r=profile.saveProfile(value)){is DataResult.Success->when(val saved=profile.saveInterests(interests)){is DataResult.Success->_state.update{it.copy(me=r.value.copy(interests=saved.value))};is DataResult.Failure->fail(saved)};is DataResult.Failure->fail(r)}}
    fun uploadPhoto(position:Int,bytes:ByteArray,mimeType:String)=task{when(val r=photos.upload(position,bytes,mimeType)){is DataResult.Success->{refreshPhotos();when(val me=profile.me()){is DataResult.Success->_state.update{it.copy(me=me.value)};is DataResult.Failure->Unit}};is DataResult.Failure->fail(r)}}
    fun deletePhoto(id:String)=task{when(val r=photos.delete(id)){is DataResult.Success->{refreshPhotos();when(val me=profile.me()){is DataResult.Success->_state.update{it.copy(me=me.value)};is DataResult.Failure->Unit}};is DataResult.Failure->fail(r)}}
    private suspend fun refreshPhotos(){when(val value=photos.list()){is DataResult.Success->_state.update{it.copy(profilePhotos=value.value)};is DataResult.Failure->fail(value)}}
    fun recover(email:String)=task{when(val r=auth.recover(email)){is DataResult.Success->_state.update{it.copy(recoveryToken=r.value.developmentToken,error=if(r.value.developmentToken==null)"If that email exists, reset instructions are on the way." else "Development reset token created.")};is DataResult.Failure->fail(r)}}
    fun resetPassword(token:String,password:String)=task{when(val r=auth.resetPassword(token,password)){is DataResult.Success->_state.update{it.copy(recoveryToken=null,error="Password updated. You can now sign in.")};is DataResult.Failure->fail(r)}}
    suspend fun loadHome()=coroutineScope{
        val discoveredTask=async{dating.discover()};val matchesTask=async{dating.matches()};val conversationsTask=async{chat.conversations()};val preferencesTask=async{dating.preferences()};val blocksTask=async{dating.blocks()};val subscriptionTask=async{dating.subscription()};val likesTask=async{dating.receivedLikes()};val photosTask=async{photos.list()}
        val discovered=discoveredTask.await();val matched=matchesTask.await();val conversations=conversationsTask.await();val preferences=preferencesTask.await();val blocks=blocksTask.await();val subscription=subscriptionTask.await();val receivedLikes=likesTask.await();val profilePhotos=photosTask.await()
        val core=listOf(discovered,matched,conversations)
        _state.update{it.copy(
            discover=(discovered as? DataResult.Success)?.value?:it.discover,
            matches=(matched as? DataResult.Success)?.value?:it.matches,
            conversations=(conversations as? DataResult.Success)?.value?:it.conversations,
            preferences=(preferences as? DataResult.Success)?.value?:it.preferences,
            blockedUsers=(blocks as? DataResult.Success)?.value?:it.blockedUsers,
            subscription=(subscription as? DataResult.Success)?.value?:it.subscription,
            receivedLikes=(receivedLikes as? DataResult.Success)?.value?:it.receivedLikes,
            profilePhotos=(profilePhotos as? DataResult.Success)?.value?:it.profilePhotos,
            offline=core.filterIsInstance<DataResult.Success<*>>().any{x->x.fromCache},
            error=core.filterIsInstance<DataResult.Failure>().firstOrNull()?.message,
            homeLoaded=true,
        )}
    }
    fun refresh()=task{loadHome()}
    fun like(userId:String,superLike:Boolean=false)=task{when(val r=likeUseCase(userId,superLike)){is DataResult.Success->{_state.update{state->state.copy(discover=state.discover.filterNot{p->p.id==userId},pendingMatch=if(r.value.matched)r.value else null,matchedProfile=if(r.value.matched)state.discover.firstOrNull{it.id==userId}else null)};if(!r.value.matched)loadHome()};is DataResult.Failure->fail(r)}}
    fun skip(userId:String)=task{when(val r=dating.pass(userId)){is DataResult.Success->{_state.update{it.copy(discover=it.discover.filterNot{p->p.id==userId})};if(_state.value.discover.isEmpty())loadHome()};is DataResult.Failure->fail(r)}}
    fun rewind()=task{when(val r=dating.rewind()){is DataResult.Success->loadHome();is DataResult.Failure->fail(r)}}
    fun consumeMatch(){_state.update{it.copy(pendingMatch=null,matchedProfile=null)}}
    fun openConversation(id:String){_state.update{it.copy(activeConversationId=id,messages=emptyList(),typing=false,peerOnline=false)};realtimeJob?.cancel();realtimeJob=viewModelScope.launch{when(val r=chat.messages(id)){is DataResult.Success->_state.update{it.copy(messages=r.value,offline=r.fromCache)};is DataResult.Failure->fail(r)};chat.markRead(id);chat.realtime(id).collect{event->when(event){is ChatEvent.Message->_state.update{it.copy(messages=(it.messages+event.value).distinctBy{m->m.id})};is ChatEvent.Typing->if(event.userId!=_state.value.me?.id)_state.update{it.copy(typing=event.active)};is ChatEvent.Online->if(event.userId!=_state.value.me?.id)_state.update{it.copy(peerOnline=event.active)};is ChatEvent.Read->_state.update{state->state.copy(messages=state.messages.map{message->if(message.id in event.messageIds)message.copy(readAt="now")else message})};is ChatEvent.Error->_state.update{it.copy(error=event.message,offline=true)};ChatEvent.Connected->_state.update{it.copy(offline=false)}}}}}
    fun sendMessage(body:String,replyToId:String?=null){val id=_state.value.activeConversationId?:return;viewModelScope.launch{when(val r=sendMessageUseCase(id,body,replyToId)){is DataResult.Success->_state.update{it.copy(messages=(it.messages+r.value).distinctBy{m->m.id})};is DataResult.Failure->fail(r)}}}
    fun sendPhoto(bytes:ByteArray,mimeType:String,replyToId:String?=null){val id=_state.value.activeConversationId?:return;task{when(val r=chat.sendPhoto(id,bytes,mimeType,replyToId)){is DataResult.Success->_state.update{it.copy(messages=(it.messages+r.value).distinctBy{m->m.id})};is DataResult.Failure->fail(r)}}}
    fun setTyping(active:Boolean){val id=_state.value.activeConversationId?:return;if(active&&!typingActive){typingActive=true;chat.sendTyping(id,true)};typingJob?.cancel();typingJob=viewModelScope.launch{if(active)delay(1_500);typingActive=false;chat.sendTyping(id,false)}}
    fun updatePreferences(value:SearchPreferences)=task{when(val r=dating.updatePreferences(value)){is DataResult.Success->{_state.update{it.copy(preferences=r.value)};loadHome()};is DataResult.Failure->fail(r)}}
    fun block(userId:String)=task{when(val r=dating.block(userId)){is DataResult.Success->{_state.update{it.copy(discover=it.discover.filterNot{p->p.id==userId},conversations=it.conversations.filterNot{c->c.otherUserId==userId})};loadHome()};is DataResult.Failure->fail(r)}}
    fun unblock(userId:String)=task{when(val r=dating.unblock(userId)){is DataResult.Success->loadHome();is DataResult.Failure->fail(r)}}
    fun unmatch(matchId:String)=task{when(val r=dating.unmatch(matchId)){is DataResult.Success->{realtimeJob?.cancel();_state.update{it.copy(activeConversationId=null,messages=emptyList())};loadHome()};is DataResult.Failure->fail(r)}}
    fun report(userId:String,reason:String,details:String)=task{when(val r=dating.report(userId,reason,details)){is DataResult.Success->_state.update{it.copy(error="Report received. Thank you for helping keep the community safe.")};is DataResult.Failure->fail(r)}}
    fun setHidden(hidden:Boolean)=task{when(val r=profile.setHidden(hidden)){is DataResult.Success->_state.update{it.copy(me=it.me?.copy(hidden=hidden))};is DataResult.Failure->fail(r)}}
    fun activatePremium()=task{when(val r=dating.activateDevelopmentPremium()){is DataResult.Success->{_state.update{it.copy(subscription=r.value)};loadHome()};is DataResult.Failure->fail(r)}}
    fun boost()=task{when(val r=dating.boost()){is DataResult.Success->_state.update{it.copy(error="Boost active for 30 minutes.")};is DataResult.Failure->fail(r)}}
    fun logout(){viewModelScope.launch{realtimeJob?.cancel();pushRegistration.unregister();auth.logout()}}
    fun deleteAccount()=task{pushRegistration.unregister();when(val r=profile.deleteAccount()){is DataResult.Success->Unit;is DataResult.Failure->fail(r)}}
    fun dismissError(){_state.update{it.copy(error=null)}}
    private fun task(block:suspend()->Unit){viewModelScope.launch{_state.update{it.copy(busy=true,error=null)};try{block()}finally{_state.update{it.copy(busy=false)}}}}
    private fun fail(value:DataResult.Failure){_state.update{it.copy(error=value.message)}}
}
