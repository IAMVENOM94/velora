package app.velora.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.velora.R
import app.velora.data.local.TokenStore
import app.velora.domain.model.*
import app.velora.domain.notification.PushRegistration
import app.velora.domain.repository.*
import app.velora.domain.usecase.*
import app.velora.location.ForegroundLocationProvider
import app.velora.location.LocationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SessionPhase { Checking, SignedOut, NeedsOnboarding, Ready }

data class UiMessage(@StringRes val resource: Int)

data class AppUiState(
    val phase: SessionPhase = SessionPhase.Checking,
    val busy: Boolean = false,
    val error: UiMessage? = null,
    val offline: Boolean = false,
    val me: MyProfile? = null,
    val profilePhotos: List<ProfilePhoto> = emptyList(),
    val discover: List<DiscoverProfile> = emptyList(),
    val matches: List<MatchSummary> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val activeConversationId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val preferences: SearchPreferences = SearchPreferences(),
    val pendingMatch: MatchResult? = null,
    val matchedProfile: DiscoverProfile? = null,
    val typing: Boolean = false,
    val peerOnline: Boolean = false,
    val homeLoaded: Boolean = false,
    val blockedUsers: List<BlockedUser> = emptyList(),
    val subscription: Subscription = Subscription(),
    val receivedLikes: ReceivedLikes = ReceivedLikes(),
    val recoveryToken: String? = null,
    val providers: AuthProviders = AuthProviders(),
    val providersLoaded: Boolean = false,
    val identities: List<AuthIdentity> = emptyList(),
    val profileLocation: ProfileLocation? = null,
    val socialProfiles: List<SocialProfile> = emptyList(),
    val phoneChallenge: PhoneOtpChallenge? = null,
    val phoneLinking: Boolean = false,
    val pendingOAuth: OAuthAuthorization? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val onboardingUseCase: CompleteOnboardingUseCase,
    private val likeUseCase: LikeProfileUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val auth: AuthRepository,
    private val profile: ProfileRepository,
    private val dating: DatingRepository,
    private val chat: ChatRepository,
    private val photos: PhotoRepository,
    private val pushRegistration: PushRegistration,
    private val locationProvider: ForegroundLocationProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var realtimeJob: Job? = null
    private var typingJob: Job? = null
    private var typingActive = false

    init {
        viewModelScope.launch { loadProviderAvailability() }
        viewModelScope.launch {
            tokenStore.hasSession.distinctUntilChanged().collectLatest { hasSession ->
                if (!hasSession) _state.update { current ->
                    AppUiState(
                        phase = SessionPhase.SignedOut,
                        providers = current.providers,
                        providersLoaded = current.providersLoaded,
                    )
                } else bootstrap()
            }
        }
    }

    private suspend fun loadProviderAvailability() {
        when (val result = auth.providers()) {
            is DataResult.Success -> _state.update { it.copy(providers = result.value, providersLoaded = true) }
            is DataResult.Failure -> _state.update { it.copy(providersLoaded = true) }
        }
    }

    private suspend fun bootstrap() {
        _state.update { it.copy(phase = SessionPhase.Checking, error = null) }
        when (val result = profile.me()) {
            is DataResult.Success -> {
                val phase = if (result.value.onboardingComplete) SessionPhase.Ready else SessionPhase.NeedsOnboarding
                _state.update { it.copy(phase = phase, me = result.value) }
                if (phase == SessionPhase.Ready) {
                    loadHome()
                    pushRegistration.register()
                }
            }
            is DataResult.Failure -> {
                if (result.message == "session_expired") {
                    auth.logout()
                    _state.update { it.copy(phase = SessionPhase.SignedOut, error = messageFor(result.message)) }
                } else {
                    _state.update { it.copy(phase = SessionPhase.Ready, offline = true, error = messageFor(result.message)) }
                    loadHome()
                }
            }
        }
    }

    fun login(email: String, password: String) = task {
        when (val result = loginUseCase(email, password)) {
            is DataResult.Success -> Unit
            is DataResult.Failure -> fail(result)
        }
    }

    fun register(email: String, password: String) = task {
        when (val result = registerUseCase(email, password)) {
            is DataResult.Success -> _state.update { it.copy(phase = SessionPhase.NeedsOnboarding) }
            is DataResult.Failure -> fail(result)
        }
    }

    fun google(credential: String, link: Boolean = false) = task {
        val result = if (link) profile.linkGoogle(credential) else auth.google(credential)
        when (result) {
            is DataResult.Success<*> -> if (link) refreshIdentities()
            is DataResult.Failure -> fail(result)
        }
    }

    fun vk(credential: String, link: Boolean = false) = task {
        val result = if (link) profile.linkVk(credential) else auth.vk(credential)
        when (result) {
            is DataResult.Success<*> -> if (link) refreshIdentities()
            is DataResult.Failure -> fail(result)
        }
    }

    fun requestPhone(phone: String, deviceId: String, link: Boolean) = task {
        when (val result = auth.requestPhone(phone, deviceId)) {
            is DataResult.Success -> _state.update { it.copy(phoneChallenge = result.value, phoneLinking = link) }
            is DataResult.Failure -> fail(result)
        }
    }

    fun verifyPhone(phone: String, code: String) = task {
        when (val result = auth.verifyPhone(phone, code, _state.value.phoneLinking)) {
            is DataResult.Success -> {
                _state.update { it.copy(phoneChallenge = null) }
                if (result.value != null) _state.update { it.copy(identities = result.value) }
            }
            is DataResult.Failure -> fail(result)
        }
    }

    fun clearPhoneChallenge() = _state.update { it.copy(phoneChallenge = null) }

    fun startOAuth(provider: String, link: Boolean = false) = task {
        when (val result = auth.oauthStart(provider, link)) {
            is DataResult.Success -> _state.update { it.copy(pendingOAuth = result.value) }
            is DataResult.Failure -> fail(result)
        }
    }

    fun consumeOAuthLaunch() = _state.update { it.copy(pendingOAuth = null) }

    fun completeOAuth(provider: String, code: String, state: String, deviceId: String?) = task {
        when (val result = auth.oauthComplete(provider, code, state, deviceId)) {
            is DataResult.Success -> if (result.value != null) _state.update { it.copy(identities = result.value) }
            is DataResult.Failure -> fail(result)
        }
    }

    fun completeOnboarding(value: ProfileDraft, interests: List<String>, showGenders: List<String>, location: ProfileLocation? = null) = task {
        when (val result = onboardingUseCase(value, interests, showGenders)) {
            is DataResult.Success -> {
                val savedLocation = location?.let { profile.saveLocation(it) }
                if (savedLocation is DataResult.Failure) fail(savedLocation)
                _state.update {
                    it.copy(
                        phase = SessionPhase.Ready,
                        me = result.value.copy(city = location?.city ?: result.value.city),
                        profileLocation = (savedLocation as? DataResult.Success)?.value ?: it.profileLocation,
                    )
                }
                loadHome()
                pushRegistration.register()
            }
            is DataResult.Failure -> fail(result)
        }
    }

    fun saveProfile(value: ProfileDraft, interests: List<String>) = task {
        when (val result = profile.saveProfile(value)) {
            is DataResult.Success -> when (val saved = profile.saveInterests(interests)) {
                is DataResult.Success -> _state.update { it.copy(me = result.value.copy(interests = saved.value)) }
                is DataResult.Failure -> fail(saved)
            }
            is DataResult.Failure -> fail(result)
        }
    }

    fun uploadPhoto(position: Int, bytes: ByteArray, mimeType: String) = task {
        when (val result = photos.upload(position, bytes, mimeType)) {
            is DataResult.Success -> {
                refreshPhotos()
                when (val me = profile.me()) {
                    is DataResult.Success -> _state.update { it.copy(me = me.value) }
                    is DataResult.Failure -> Unit
                }
            }
            is DataResult.Failure -> fail(result)
        }
    }

    fun deletePhoto(id: String) = task {
        when (val result = photos.delete(id)) {
            is DataResult.Success -> {
                refreshPhotos()
                when (val me = profile.me()) {
                    is DataResult.Success -> _state.update { it.copy(me = me.value) }
                    is DataResult.Failure -> Unit
                }
            }
            is DataResult.Failure -> fail(result)
        }
    }

    private suspend fun refreshPhotos() {
        when (val value = photos.list()) {
            is DataResult.Success -> _state.update { it.copy(profilePhotos = value.value) }
            is DataResult.Failure -> fail(value)
        }
    }

    fun recover(email: String) = task {
        when (val result = auth.recover(email)) {
            is DataResult.Success -> _state.update {
                it.copy(
                    recoveryToken = result.value.developmentToken,
                    error = UiMessage(if (result.value.developmentToken == null) R.string.recovery_sent else R.string.recovery_dev_created),
                )
            }
            is DataResult.Failure -> fail(result)
        }
    }

    fun resetPassword(token: String, password: String) = task {
        when (val result = auth.resetPassword(token, password)) {
            is DataResult.Success -> _state.update { it.copy(recoveryToken = null, error = UiMessage(R.string.password_updated)) }
            is DataResult.Failure -> fail(result)
        }
    }

    suspend fun loadHome() = coroutineScope {
        val discoveredTask = async { dating.discover() }
        val matchesTask = async { dating.matches() }
        val conversationsTask = async { chat.conversations() }
        val preferencesTask = async { dating.preferences() }
        val blocksTask = async { dating.blocks() }
        val subscriptionTask = async { dating.subscription() }
        val likesTask = async { dating.receivedLikes() }
        val photosTask = async { photos.list() }
        val identitiesTask = async { profile.identities() }
        val locationTask = async { profile.location() }
        val socialTask = async { profile.socialProfiles() }
        val discovered = discoveredTask.await()
        val matched = matchesTask.await()
        val conversations = conversationsTask.await()
        val preferences = preferencesTask.await()
        val blocks = blocksTask.await()
        val subscription = subscriptionTask.await()
        val receivedLikes = likesTask.await()
        val profilePhotos = photosTask.await()
        val identities = identitiesTask.await()
        val location = locationTask.await()
        val social = socialTask.await()
        val core = listOf(discovered, matched, conversations)
        _state.update {
            it.copy(
                discover = (discovered as? DataResult.Success)?.value ?: it.discover,
                matches = (matched as? DataResult.Success)?.value ?: it.matches,
                conversations = (conversations as? DataResult.Success)?.value ?: it.conversations,
                preferences = (preferences as? DataResult.Success)?.value ?: it.preferences,
                blockedUsers = (blocks as? DataResult.Success)?.value ?: it.blockedUsers,
                subscription = (subscription as? DataResult.Success)?.value ?: it.subscription,
                receivedLikes = (receivedLikes as? DataResult.Success)?.value ?: it.receivedLikes,
                profilePhotos = (profilePhotos as? DataResult.Success)?.value ?: it.profilePhotos,
                identities = (identities as? DataResult.Success)?.value ?: it.identities,
                profileLocation = (location as? DataResult.Success)?.value ?: it.profileLocation,
                socialProfiles = (social as? DataResult.Success)?.value ?: it.socialProfiles,
                offline = core.filterIsInstance<DataResult.Success<*>>().any { value -> value.fromCache },
                error = core.filterIsInstance<DataResult.Failure>().firstOrNull()?.let { failure -> messageFor(failure.message) },
                homeLoaded = true,
            )
        }
    }

    fun refresh() = task { loadHome() }
    fun like(userId: String, superLike: Boolean = false) = task {
        when (val result = likeUseCase(userId, superLike)) {
            is DataResult.Success -> {
                _state.update { state -> state.copy(discover = state.discover.filterNot { it.id == userId }, pendingMatch = if (result.value.matched) result.value else null, matchedProfile = if (result.value.matched) state.discover.firstOrNull { it.id == userId } else null) }
                if (!result.value.matched) loadHome()
            }
            is DataResult.Failure -> fail(result)
        }
    }

    fun skip(userId: String) = task { when (val result = dating.pass(userId)) { is DataResult.Success -> { _state.update { it.copy(discover = it.discover.filterNot { person -> person.id == userId }) }; if (_state.value.discover.isEmpty()) loadHome() }; is DataResult.Failure -> fail(result) } }
    fun rewind() = task { when (val result = dating.rewind()) { is DataResult.Success -> loadHome(); is DataResult.Failure -> fail(result) } }
    fun consumeMatch() = _state.update { it.copy(pendingMatch = null, matchedProfile = null) }

    fun openConversation(id: String) {
        _state.update { it.copy(activeConversationId = id, messages = emptyList(), typing = false, peerOnline = false) }
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            when (val result = chat.messages(id)) { is DataResult.Success -> _state.update { it.copy(messages = result.value, offline = result.fromCache) }; is DataResult.Failure -> fail(result) }
            chat.markRead(id)
            chat.realtime(id).collect { event ->
                when (event) {
                    is ChatEvent.Message -> _state.update { it.copy(messages = (it.messages + event.value).distinctBy { message -> message.id }) }
                    is ChatEvent.Typing -> if (event.userId != _state.value.me?.id) _state.update { it.copy(typing = event.active) }
                    is ChatEvent.Online -> if (event.userId != _state.value.me?.id) _state.update { it.copy(peerOnline = event.active) }
                    is ChatEvent.Read -> _state.update { state -> state.copy(messages = state.messages.map { message -> if (message.id in event.messageIds) message.copy(readAt = "now") else message }) }
                    is ChatEvent.Error -> _state.update { it.copy(error = UiMessage(R.string.error_server), offline = true) }
                    ChatEvent.Connected -> _state.update { it.copy(offline = false) }
                }
            }
        }
    }

    fun sendMessage(body: String, replyToId: String? = null) { val id = _state.value.activeConversationId ?: return; viewModelScope.launch { when (val result = sendMessageUseCase(id, body, replyToId)) { is DataResult.Success -> _state.update { it.copy(messages = (it.messages + result.value).distinctBy { message -> message.id }) }; is DataResult.Failure -> fail(result) } } }
    fun sendPhoto(bytes: ByteArray, mimeType: String, replyToId: String? = null) { val id = _state.value.activeConversationId ?: return; task { when (val result = chat.sendPhoto(id, bytes, mimeType, replyToId)) { is DataResult.Success -> _state.update { it.copy(messages = (it.messages + result.value).distinctBy { message -> message.id }) }; is DataResult.Failure -> fail(result) } } }
    fun setTyping(active: Boolean) { val id = _state.value.activeConversationId ?: return; if (active && !typingActive) { typingActive = true; chat.sendTyping(id, true) }; typingJob?.cancel(); typingJob = viewModelScope.launch { if (active) delay(1_500); typingActive = false; chat.sendTyping(id, false) } }
    fun updatePreferences(value: SearchPreferences) = task { when (val result = dating.updatePreferences(value)) { is DataResult.Success -> { _state.update { it.copy(preferences = result.value) }; loadHome() }; is DataResult.Failure -> fail(result) } }
    fun block(userId: String) = task { when (val result = dating.block(userId)) { is DataResult.Success -> { _state.update { it.copy(discover = it.discover.filterNot { person -> person.id == userId }, conversations = it.conversations.filterNot { conversation -> conversation.otherUserId == userId }) }; loadHome() }; is DataResult.Failure -> fail(result) } }
    fun unblock(userId: String) = task { when (val result = dating.unblock(userId)) { is DataResult.Success -> loadHome(); is DataResult.Failure -> fail(result) } }
    fun unmatch(matchId: String) = task { when (val result = dating.unmatch(matchId)) { is DataResult.Success -> { realtimeJob?.cancel(); _state.update { it.copy(activeConversationId = null, messages = emptyList()) }; loadHome() }; is DataResult.Failure -> fail(result) } }
    fun report(userId: String, reason: String, details: String) = task { when (val result = dating.report(userId, reason, details)) { is DataResult.Success -> _state.update { it.copy(error = UiMessage(R.string.report_received)) }; is DataResult.Failure -> fail(result) } }
    fun setHidden(hidden: Boolean) = task { when (val result = profile.setHidden(hidden)) { is DataResult.Success -> _state.update { it.copy(me = it.me?.copy(hidden = hidden)) }; is DataResult.Failure -> fail(result) } }
    fun activatePremium() = task { when (val result = dating.activateDevelopmentPremium()) { is DataResult.Success -> { _state.update { it.copy(subscription = result.value) }; loadHome() }; is DataResult.Failure -> fail(result) } }
    fun boost() = task { when (val result = dating.boost()) { is DataResult.Success -> _state.update { it.copy(error = UiMessage(R.string.boost_active)) }; is DataResult.Failure -> fail(result) } }

    fun savePreferredLocale(languageTag: String?) { viewModelScope.launch { if (_state.value.me?.onboardingComplete == true) profile.saveLocale(languageTag) } }

    fun updateLocationFromDevice() = task {
        when (val located = locationProvider.current()) {
            is LocationResult.Located -> saveResolvedLocation(located.value)
            LocationResult.PermissionDenied -> _state.update { it.copy(error = UiMessage(R.string.location_denied)) }
            LocationResult.Unavailable -> _state.update { it.copy(error = UiMessage(R.string.location_unavailable)) }
        }
    }

    fun updateManualLocation(country: String?, city: String) = task {
        if (city.isBlank()) { _state.update { it.copy(error = UiMessage(R.string.error_city_required)) }; return@task }
        saveResolvedLocation(locationProvider.manual(country, city))
    }

    private suspend fun saveResolvedLocation(value: ProfileLocation) {
        when (val result = profile.saveLocation(value)) {
            is DataResult.Success -> _state.update { it.copy(profileLocation = result.value, me = it.me?.copy(city = result.value.city), error = UiMessage(R.string.location_saved)) }
            is DataResult.Failure -> fail(result)
        }
    }

    fun unlinkIdentity(providerName: String) = task { when (val result = profile.unlinkIdentity(providerName)) { is DataResult.Success -> refreshIdentities(); is DataResult.Failure -> fail(result) } }
    private suspend fun refreshIdentities() { when (val result = profile.identities()) { is DataResult.Success -> _state.update { it.copy(identities = result.value) }; is DataResult.Failure -> fail(result) } }
    fun saveInstagram(username: String, visible: Boolean) = task { when (val result = profile.saveInstagram(username, visible)) { is DataResult.Success -> _state.update { it.copy(socialProfiles = (it.socialProfiles.filterNot { social -> social.provider == "instagram" } + result.value)) }; is DataResult.Failure -> fail(result) } }
    fun removeInstagram() = task { when (val result = profile.removeInstagram()) { is DataResult.Success -> _state.update { it.copy(socialProfiles = it.socialProfiles.filterNot { social -> social.provider == "instagram" }) }; is DataResult.Failure -> fail(result) } }

    fun logout() { viewModelScope.launch { realtimeJob?.cancel(); pushRegistration.unregister(); auth.logout() } }
    fun deleteAccount() = task { pushRegistration.unregister(); when (val result = profile.deleteAccount()) { is DataResult.Success -> Unit; is DataResult.Failure -> fail(result) } }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun showMessage(@StringRes resource: Int) = _state.update { it.copy(error = UiMessage(resource)) }

    private fun task(block: suspend () -> Unit) { viewModelScope.launch { _state.update { it.copy(busy = true, error = null) }; try { block() } finally { _state.update { it.copy(busy = false) } } } }
    private fun fail(value: DataResult.Failure) = _state.update { it.copy(error = messageFor(value.message)) }

    private fun messageFor(code: String): UiMessage = UiMessage(when (code.lowercase()) {
        "invalid_email" -> R.string.error_valid_email
        "invalid_password_length" -> R.string.error_password_length
        "name_required" -> R.string.error_name_required
        "city_required" -> R.string.error_city_required
        "adult_required" -> R.string.error_adult_required
        "interests_required" -> R.string.error_interests_required
        "preferences_required" -> R.string.error_preferences_required
        "message_empty" -> R.string.error_message_empty
        "bad_data", "otp_incorrect", "otp_expired", "otp_consumed" -> R.string.error_bad_data
        "session_expired" -> R.string.error_session_expired
        "premium_required" -> R.string.error_premium_required
        "not_found" -> R.string.error_not_found
        "conflict", "identity_conflict", "provider_already_linked", "last_identity" -> R.string.error_conflict
        "file_large" -> R.string.error_file_large
        "rate_limit", "otp_rate_limit", "otp_cooldown", "otp_locked" -> R.string.error_rate_limit
        "offline" -> R.string.error_offline
        "invalid_auth_response" -> R.string.invalid_credentials_response
        "unexpected" -> R.string.error_unexpected
        else -> R.string.error_server
    })
}
