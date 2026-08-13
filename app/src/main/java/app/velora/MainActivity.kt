@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.velora

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.velora.auth.*
import app.velora.domain.model.*
import app.velora.localization.AppLanguage
import app.velora.localization.LocaleController
import app.velora.location.ForegroundLocationProvider
import app.velora.location.LocationResult
import app.velora.presentation.*
import app.velora.ui.theme.*
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var foregroundLocationProvider: ForegroundLocationProvider
    private var oauthCallbackUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthCallbackUri = intent?.data
        setContent { VeloraTheme { VeloraApp(this, oauthCallbackUri, { oauthCallbackUri = null }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        oauthCallbackUri = intent.data
    }
}

enum class Screen { Splash, Login, Register, Recover, Phone, Onboarding, Discover, Detail, Match, Matches, Chats, Chat, Profile, Settings, Premium }
private val Screen.route get() = name.lowercase()

data class Person(
    val id: String,
    val name: String,
    val age: Int,
    val city: String,
    val distanceKm: Int?,
    val bio: String,
    val image: Int,
    val online: Boolean = true,
    val photoUrl: String? = null,
    val interests: List<String> = emptyList(),
    val goal: String = "unknown",
    val photoUrls: List<String> = emptyList(),
)

fun DiscoverProfile.toPerson() = Person(id, name, age, city, distanceKm, bio, if (name.equals("Sofia", true)) R.drawable.sofia else R.drawable.maya, online, photoUrl, interests, goal, photoUrls)

@Composable
fun VeloraApp(activity: MainActivity, callbackUri: Uri?, consumeCallback: () -> Unit, vm: MainViewModel = viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val google = remember { GoogleCredentialManagerAdapter() }
    val vk = remember { VkIdAuthProviderAdapter() }
    val browser = remember { BrowserOAuthAdapter() }
    var phoneLinkMode by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by nav.currentBackStackEntryAsState()
    val current = Screen.entries.firstOrNull { it.route == backStackEntry?.destination?.route } ?: Screen.Splash
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(800); splashDone = true }

    fun navigate(target: Screen, root: Boolean = false) {
        nav.navigate(target.route) {
            launchSingleTop = true
            restoreState = !root
            if (root) popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    fun googleAuth(link: Boolean) = scope.launch {
        when (val result = google.acquire(activity)) {
            is ProviderCredentialResult.Success -> vm.google(result.credential, link)
            ProviderCredentialResult.Cancelled -> Unit
            ProviderCredentialResult.Unavailable -> vm.showMessage(R.string.not_configured)
            is ProviderCredentialResult.Failure -> vm.showMessage(R.string.provider_error)
        }
    }

    fun vkAuth(link: Boolean) = scope.launch {
        when (val result = vk.acquire(activity)) {
            is ProviderCredentialResult.Success -> vm.vk(result.credential, link)
            ProviderCredentialResult.Cancelled -> Unit
            ProviderCredentialResult.Unavailable -> vm.showMessage(R.string.not_configured)
            is ProviderCredentialResult.Failure -> vm.showMessage(R.string.provider_error)
        }
    }

    LaunchedEffect(splashDone, ui.phase) {
        if (!splashDone) return@LaunchedEffect
        when (ui.phase) {
            SessionPhase.Checking -> Unit
            SessionPhase.SignedOut -> if (current !in listOf(Screen.Login, Screen.Register, Screen.Recover, Screen.Phone)) navigate(Screen.Login, true)
            SessionPhase.NeedsOnboarding -> if (current != Screen.Onboarding) navigate(Screen.Onboarding, true)
            SessionPhase.Ready -> if (current in listOf(Screen.Splash, Screen.Login, Screen.Register, Screen.Recover, Screen.Phone, Screen.Onboarding)) navigate(Screen.Discover, true)
        }
    }
    LaunchedEffect(ui.pendingMatch?.matchId) { if (ui.pendingMatch != null && current != Screen.Match) navigate(Screen.Match) }
    LaunchedEffect(ui.pendingOAuth?.authorizationUrl) {
        ui.pendingOAuth?.let { browser.launch(activity, it.authorizationUrl); vm.consumeOAuthLaunch() }
    }
    LaunchedEffect(callbackUri) {
        browser.callback(callbackUri)?.let { vm.completeOAuth(it.provider, it.code, it.state, it.deviceId) }
        if (callbackUri != null) consumeCallback()
    }

    val go: (Screen) -> Unit = { target ->
        if (target != current) nav.navigate(target.route) {
            popUpTo(Screen.Discover.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val activePerson = ui.discover.firstOrNull()?.toPerson()
    val activeConversation = ui.conversations.firstOrNull { it.id == ui.activeConversationId }
    val openChat: (String?) -> Unit = { id ->
        val chosen = id ?: ui.conversations.firstOrNull()?.id ?: ui.pendingMatch?.conversationId
        if (chosen != null) { vm.openConversation(chosen); navigate(Screen.Chat) }
    }

    CompositionLocalProvider(LocalContentColor provides White) {
        Box(Modifier.fillMaxSize().background(Ink)) {
            NavHost(nav, Screen.Splash.route, enterTransition = { fadeIn(tween(260)) }, exitTransition = { fadeOut(tween(180)) }, popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(160)) }) {
                composable(Screen.Splash.route) { Splash() }
                composable(Screen.Login.route) {
                    Login(
                        busy = ui.busy,
                        providers = ui.providers,
                        googleLocallyConfigured = google.configured,
                        vkLocallyConfigured = vk.configured,
                        onCreate = { navigate(Screen.Register) },
                        onForgot = { navigate(Screen.Recover) },
                        onLogin = vm::login,
                        onGoogle = { googleAuth(false) },
                        onPhone = { phoneLinkMode = false; navigate(Screen.Phone) },
                        onX = { vm.startOAuth("x") },
                        onVk = { vkAuth(false) },
                    )
                }
                composable(Screen.Register.route) { Register(ui.busy, { nav.popBackStack() }, vm::register) }
                composable(Screen.Recover.route) { Recovery(ui.recoveryToken, { nav.popBackStack() }, vm::recover, vm::resetPassword) }
                composable(Screen.Phone.route) {
                    PhoneAuthScreen(
                        challenge = ui.phoneChallenge,
                        linking = phoneLinkMode,
                        busy = ui.busy,
                        onBack = { vm.clearPhoneChallenge(); nav.popBackStack() },
                        onRequest = { phone -> vm.requestPhone(phone, deviceId(activity), phoneLinkMode) },
                        onVerify = vm::verifyPhone,
                    )
                }
                composable(Screen.Onboarding.route) {
                    OnboardingConnected(
                        onDone = { profile, interests, genders -> vm.completeOnboarding(profile, interests, genders) },
                        onPhoto = vm::uploadPhoto,
                        locationProvider = activity.foregroundLocationProvider,
                        onDoneWithLocation = vm::completeOnboarding,
                    )
                }
                composable(Screen.Discover.route) { MainShell(Screen.Discover, go) { if (activePerson == null) DiscoverEmpty(ui.homeLoaded, ui.subscription.premium, vm::refresh, { navigate(Screen.Settings) }, vm::rewind) else DiscoverConnected(activePerson, { navigate(Screen.Detail) }, { vm.skip(activePerson.id) }, { vm.like(activePerson.id) }, { vm.like(activePerson.id, true) }, if (ui.subscription.premium) vm::rewind else null) } }
                composable(Screen.Detail.route) { if (activePerson == null) DiscoverEmpty(ui.homeLoaded, ui.subscription.premium, { navigate(Screen.Discover) }, { navigate(Screen.Settings) }, vm::rewind) else ProfileDetailConnected(activePerson, { nav.popBackStack() }, { vm.like(activePerson.id) }, { vm.like(activePerson.id, true) }, { vm.block(activePerson.id); navigate(Screen.Discover) }, { vm.report(activePerson.id, "other", "profile") }) }
                composable(Screen.Match.route) { MatchConnected(ui.me, ui.matchedProfile, { val id = ui.pendingMatch?.conversationId; vm.consumeMatch(); nav.popBackStack(); openChat(id) }, { vm.consumeMatch(); if (!nav.popBackStack(Screen.Discover.route, false)) navigate(Screen.Discover, true) }) }
                composable(Screen.Matches.route) { MainShell(Screen.Matches, go) { MatchesConnected(ui.matches, ui.conversations, openChat) } }
                composable(Screen.Chats.route) { MainShell(Screen.Chats, go) { ChatsConnected(ui.conversations, openChat) } }
                composable(Screen.Chat.route) { ChatConnected(ui.messages, ui.me?.id, activeConversation, ui.typing, ui.peerOnline, { nav.popBackStack() }, vm::sendMessage, vm::sendPhoto, vm::setTyping, { activeConversation?.matchId?.let(vm::unmatch); navigate(Screen.Chats) }, { activeConversation?.otherUserId?.let(vm::block); navigate(Screen.Chats) }, { activeConversation?.otherUserId?.let { vm.report(it, "other", "conversation") } }) }
                composable(Screen.Profile.route) { MainShell(Screen.Profile, go) { MyProfileConnected(ui.me, ui.profilePhotos, { navigate(Screen.Settings) }, { navigate(Screen.Premium) }, vm::saveProfile, vm::uploadPhoto, vm::deletePhoto, ui.profileLocation, ui.socialProfiles) } }
                composable(Screen.Settings.route) {
                    SettingsConnected(
                        initial = ui.preferences,
                        initialHidden = ui.me?.hidden == true,
                        blocked = ui.blockedUsers,
                        onBack = { nav.popBackStack() },
                        onSave = vm::updatePreferences,
                        onHidden = vm::setHidden,
                        onUnblock = vm::unblock,
                        onLogout = vm::logout,
                        onDelete = vm::deleteAccount,
                        providers = ui.providers,
                        identities = ui.identities,
                        location = ui.profileLocation,
                        socialProfiles = ui.socialProfiles,
                        googleConfigured = google.configured,
                        vkConfigured = vk.configured,
                        onLanguage = { language -> LocaleController.apply(language); vm.savePreferredLocale(language.languageTag) },
                        onDeviceLocation = vm::updateLocationFromDevice,
                        onManualLocation = vm::updateManualLocation,
                        onGoogle = { googleAuth(true) },
                        onPhone = { phoneLinkMode = true; navigate(Screen.Phone) },
                        onX = { vm.startOAuth("x", true) },
                        onVk = { vkAuth(true) },
                        onUnlink = vm::unlinkIdentity,
                        onSaveInstagram = vm::saveInstagram,
                        onRemoveInstagram = vm::removeInstagram,
                    )
                }
                composable(Screen.Premium.route) { PremiumConnected(ui.subscription, ui.receivedLikes, { nav.popBackStack() }, vm::activatePremium, vm::boost) }
            }
            if (ui.busy) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp).size(24.dp), color = Pink, strokeWidth = 3.dp)
            ui.error?.let { message -> Snackbar(Modifier.align(Alignment.BottomCenter).padding(18.dp), action = { TextButton(vm::dismissError) { Text(stringResource(R.string.dismiss)) } }) { Text(stringResource(message.resource)) } }
        }
    }
}

private fun deviceId(activity: MainActivity): String {
    val preferences = activity.getSharedPreferences("velora_installation", android.content.Context.MODE_PRIVATE)
    return preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString("installation_id", it).apply()
    }
}

@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) = Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x332E1656), Ink), center = Offset(800f, 100f), radius = 1000f)), content = content)

@Composable
fun BrandMark(size: Dp = 64.dp) = Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Pink, Violet))), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Favorite, null, tint = White, modifier = Modifier.size(size * .48f)) }

@Composable
fun Splash() = AuroraBackground { Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) { BrandMark(82.dp); Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.app_name).uppercase(), style = MaterialTheme.typography.displayLarge); Text(stringResource(R.string.splash_tagline), color = Muted, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp) } }

@Composable
fun AuthHeader(title: String, subtitle: String) { BrandMark(54.dp); Spacer(Modifier.height(24.dp)); Text(title, style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(8.dp)); Text(subtitle, color = Muted) }

@Composable
fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) = Button(onClick, modifier.height(56.dp), enabled = enabled, shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Pink)) { if (icon != null) { Icon(icon, null); Spacer(Modifier.width(9.dp)) }; Text(text, fontWeight = FontWeight.Bold) }

@Composable
fun DarkField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier, password: Boolean = false) = OutlinedTextField(value, onValue, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pink, unfocusedBorderColor = Color(0xFF3A3342), focusedContainerColor = Glass, unfocusedContainerColor = Glass))

@Composable
private fun ProviderButton(text: String, enabled: Boolean, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.fillMaxWidth().height(52.dp), enabled = enabled, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (enabled) Color(0x554F405E) else Color(0x224F405E))) { Icon(icon, null); Spacer(Modifier.width(10.dp)); Text(text); Spacer(Modifier.weight(1f)); if (!enabled) Text(stringResource(R.string.not_configured), color = Muted, style = MaterialTheme.typography.labelSmall) }
}

@Composable
fun Login(busy: Boolean, providers: AuthProviders = AuthProviders(), googleLocallyConfigured: Boolean = false, vkLocallyConfigured: Boolean = false, onCreate: () -> Unit, onForgot: () -> Unit, onLogin: (String, String) -> Unit, onGoogle: () -> Unit = {}, onPhone: () -> Unit = {}, onX: () -> Unit = {}, onVk: () -> Unit = {}) = AuroraBackground {
    var email by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top = 42.dp)) {
        AuthHeader(stringResource(R.string.login_title), stringResource(R.string.login_subtitle)); Spacer(Modifier.height(28.dp))
        DarkField(email, { email = it }, stringResource(R.string.email)); Spacer(Modifier.height(12.dp)); DarkField(pass, { pass = it }, stringResource(R.string.password), password = true)
        TextButton(onForgot, Modifier.align(Alignment.End)) { Text(stringResource(R.string.forgot_password), color = Pink) }
        GlowButton(stringResource(if (busy) R.string.signing_in else R.string.sign_in), { if (!busy) onLogin(email, pass) }, Modifier.fillMaxWidth(), enabled = !busy)
        Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.or_continue_with), color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(12.dp))
        ProviderButton(stringResource(R.string.continue_google), providers.google && googleLocallyConfigured, Icons.Default.AccountCircle, onGoogle); Spacer(Modifier.height(8.dp))
        ProviderButton(stringResource(R.string.continue_phone), providers.phone, Icons.Default.Phone, onPhone); Spacer(Modifier.height(8.dp))
        ProviderButton(stringResource(R.string.continue_x), providers.x, Icons.Default.AlternateEmail, onX); Spacer(Modifier.height(8.dp))
        ProviderButton(stringResource(R.string.continue_vk), providers.vk && vkLocallyConfigured, Icons.Default.Public, onVk)
        Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Text(stringResource(R.string.new_to_app, stringResource(R.string.app_name)), color = Muted); Text(stringResource(R.string.create_account), color = Pink, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onCreate)) }
        Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.legal_18), color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Register(busy: Boolean, onBack: () -> Unit, onContinue: (String, String) -> Unit) = AuroraBackground {
    var email by remember { mutableStateOf("") }; var pass by remember { mutableStateOf("") }; var repeat by remember { mutableStateOf("") }; var mismatch by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top = 32.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }; Spacer(Modifier.height(18.dp)); AuthHeader(stringResource(R.string.register_title), stringResource(R.string.register_subtitle)); Spacer(Modifier.height(30.dp))
        DarkField(email, { email = it }, stringResource(R.string.email)); Spacer(Modifier.height(14.dp)); DarkField(pass, { pass = it }, stringResource(R.string.create_password), password = true); Spacer(Modifier.height(14.dp)); DarkField(repeat, { repeat = it }, stringResource(R.string.repeat_password), password = true)
        if (mismatch) Text(stringResource(R.string.passwords_mismatch), color = Pink, modifier = Modifier.padding(top = 8.dp)); Spacer(Modifier.height(24.dp)); GlowButton(stringResource(if (busy) R.string.creating_account else R.string.continue_action), { mismatch = pass != repeat; if (!busy && !mismatch) onContinue(email, pass) }, Modifier.fillMaxWidth(), enabled = !busy); Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.auth_protected), color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Recovery(developmentToken: String?, onBack: () -> Unit, onRecover: (String) -> Unit, onReset: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }; var requested by remember { mutableStateOf(false) }; var token by remember(developmentToken) { mutableStateOf(developmentToken.orEmpty()) }; var password by remember { mutableStateOf("") }
    AuroraBackground { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top = 32.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }; Spacer(Modifier.height(18.dp)); AuthHeader(stringResource(R.string.restore_access), stringResource(R.string.restore_subtitle)); Spacer(Modifier.height(30.dp)); DarkField(email, { email = it }, stringResource(R.string.account_email)); Spacer(Modifier.height(18.dp)); GlowButton(stringResource(R.string.send_reset), { requested = true; onRecover(email) }, Modifier.fillMaxWidth())
        if (requested) { Spacer(Modifier.height(30.dp)); Text(stringResource(R.string.enter_email_token), style = MaterialTheme.typography.titleLarge); if (developmentToken != null) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.development_token), color = Violet) }; Spacer(Modifier.height(16.dp)); DarkField(token, { token = it }, stringResource(R.string.reset_token)); Spacer(Modifier.height(12.dp)); DarkField(password, { password = it }, stringResource(R.string.new_password), password = true); Spacer(Modifier.height(18.dp)); GlowButton(stringResource(R.string.update_password), { onReset(token, password) }, Modifier.fillMaxWidth()) }
    } }
}

@Composable
fun PhoneAuthScreen(challenge: PhoneOtpChallenge?, linking: Boolean, busy: Boolean, onBack: () -> Unit, onRequest: (String) -> Unit, onVerify: (String, String) -> Unit) {
    var prefix by rememberSaveable { mutableStateOf("+7") }; var number by rememberSaveable { mutableStateOf("") }; var code by remember(challenge) { mutableStateOf(challenge?.developmentCode.orEmpty()) }; var resendSeconds by remember(challenge) { mutableIntStateOf(challenge?.resendAfterSeconds ?: 0) }
    LaunchedEffect(resendSeconds) { if (resendSeconds > 0) { delay(1_000); resendSeconds-- } }
    val phone = prefix + number.filter(Char::isDigit)
    AuroraBackground { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top = 32.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }; Spacer(Modifier.height(18.dp)); AuthHeader(stringResource(R.string.phone_title), stringResource(R.string.phone_subtitle)); Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { DarkField(prefix, { prefix = it.filter { char -> char == '+' || char.isDigit() }.take(4) }, stringResource(R.string.country_code), modifier = Modifier.width(112.dp)); DarkField(number, { number = it.filter(Char::isDigit).take(14) }, stringResource(R.string.phone_number), modifier = Modifier.weight(1f)) }
        Spacer(Modifier.height(16.dp)); GlowButton(if (resendSeconds > 0) stringResource(R.string.resend_in, resendSeconds) else stringResource(R.string.send_code), { onRequest(phone) }, Modifier.fillMaxWidth(), enabled = !busy && number.length >= 6 && resendSeconds == 0)
        if (challenge != null) { Spacer(Modifier.height(28.dp)); DarkField(code, { code = it.filter(Char::isDigit).take(6) }, stringResource(R.string.verification_code)); challenge.developmentCode?.let { Text(stringResource(R.string.development_otp, it), color = Violet, modifier = Modifier.padding(top = 8.dp)) }; Spacer(Modifier.height(16.dp)); GlowButton(stringResource(if (linking) R.string.verify_connect else R.string.verify_continue), { onVerify(phone, code) }, Modifier.fillMaxWidth(), enabled = !busy && code.length == 6) }
    } }
}

@Composable
fun MainShell(screen: Screen, go: (Screen) -> Unit, content: @Composable () -> Unit) {
    val items = listOf(Triple(Screen.Discover, R.string.nav_discover, Icons.Outlined.Explore), Triple(Screen.Matches, R.string.nav_matches, Icons.Outlined.FavoriteBorder), Triple(Screen.Chats, R.string.nav_chats, Icons.Outlined.ChatBubbleOutline), Triple(Screen.Profile, R.string.nav_profile, Icons.Outlined.PersonOutline))
    Scaffold(containerColor = Color.Transparent, bottomBar = { NavigationBar(containerColor = Color(0xF5110D16), tonalElevation = 0.dp) { items.forEach { (target, label, icon) -> NavigationBarItem(screen == target, { go(target) }, { Icon(icon, stringResource(label)) }, label = { Text(stringResource(label)) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Pink, selectedTextColor = Pink, indicatorColor = Color(0x332E1656), unselectedIconColor = Muted, unselectedTextColor = Muted)) } } }) { padding -> Box(Modifier.padding(padding)) { content() } }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) = Column(modifier.clip(RoundedCornerShape(24.dp)).background(Glass).border(1.dp, Color(0x224F405E), RoundedCornerShape(24.dp)).padding(padding), content = content)

@Composable
fun Action(icon: ImageVector, color: Color, size: Dp, description: String, onClick: () -> Unit) = IconButton(onClick, Modifier.size(size).background(Color(0xFF201927), CircleShape).border(1.dp, color.copy(.5f), CircleShape)) { Icon(icon, description, tint = color, modifier = Modifier.size(size * .42f)) }

@Composable
fun Top(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) = Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }; Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); action?.invoke() }

@Composable
fun SettingsHeader(text: String) { Spacer(Modifier.height(24.dp)); Text(text, color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(9.dp)) }

@Composable
fun localizedInterest(value: String): String {
    val resource = when (value.lowercase()) { "travel" -> R.string.interest_travel; "coffee" -> R.string.interest_coffee; "design" -> R.string.interest_design; "live music" -> R.string.interest_live_music; "hiking" -> R.string.interest_hiking; "cooking" -> R.string.interest_cooking; "photography" -> R.string.interest_photography; "books" -> R.string.interest_books; else -> null }
    return resource?.let { stringResource(it) } ?: value
}

@Composable
fun localizedGoal(value: String): String = stringResource(when (value) { "relationship" -> R.string.goal_relationship; "communication" -> R.string.goal_conversations; "dating" -> R.string.goal_dating; "friendship" -> R.string.goal_friendship; else -> R.string.goal_unknown })

@Composable
fun OnboardingConnected(
    onDone: (ProfileDraft, List<String>, List<String>) -> Unit,
    onPhoto: (Int, ByteArray, String) -> Unit,
    initialUploadedPositions: Set<Int> = emptySet(),
    locationProvider: ForegroundLocationProvider? = null,
    onDoneWithLocation: ((ProfileDraft, List<String>, List<String>, ProfileLocation?) -> Unit)? = null,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }; var name by rememberSaveable { mutableStateOf("") }; var birth by rememberSaveable { mutableStateOf("1997-04-12") }; var gender by rememberSaveable { mutableStateOf("man") }; var lookingFor by rememberSaveable { mutableStateOf("woman") }; var city by rememberSaveable { mutableStateOf("") }; var country by rememberSaveable { mutableStateOf("") }; var bio by rememberSaveable { mutableStateOf("") }; var goal by rememberSaveable { mutableStateOf("relationship") }; var selectedInterests by rememberSaveable { mutableStateOf(setOf<String>()) }; var uploadedPositions by remember { mutableStateOf(initialUploadedPositions) }; var pendingPosition by remember { mutableIntStateOf(0) }; var location by remember { mutableStateOf<ProfileLocation?>(null) }; var locating by remember { mutableStateOf(false) }; var locationNotice by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val titles = stringArrayResource(R.array.onboarding_titles); val subtitles = stringArrayResource(R.array.onboarding_subtitles)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { val mime = context.contentResolver.getType(uri) ?: "image/jpeg"; val position = pendingPosition; uploadedPositions += position; scope.launch(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { onPhoto(position, it.readBytes(), mime) } } } }
    fun locate() { if (locationProvider == null) return; locating = true; scope.launch { when (val result = locationProvider.current()) { is LocationResult.Located -> { location = result.value; city = result.value.city; country = result.value.country.orEmpty(); locationNotice = null }; LocationResult.PermissionDenied -> locationNotice = R.string.location_denied; LocationResult.Unavailable -> locationNotice = R.string.location_unavailable }; locating = false } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants -> if (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) locate() else locationNotice = R.string.location_denied }
    val genderOptions = listOf(R.string.gender_woman to "woman", R.string.gender_man to "man", R.string.gender_non_binary to "non_binary", R.string.gender_self_describe to "other")
    val lookingOptions = listOf(R.string.show_women to "woman", R.string.show_men to "man", R.string.show_everyone to "everyone")
    val interestOptions = listOf(R.string.interest_travel to "Travel", R.string.interest_coffee to "Coffee", R.string.interest_design to "Design", R.string.interest_live_music to "Live music", R.string.interest_hiking to "Hiking", R.string.interest_cooking to "Cooking", R.string.interest_photography to "Photography", R.string.interest_books to "Books")
    val goalOptions = listOf(R.string.goal_relationship to "relationship", R.string.goal_conversations to "communication", R.string.goal_dating to "dating", R.string.goal_friendship to "friendship", R.string.goal_unknown to "unknown")
    val options = when (step) { 2 -> genderOptions; 3 -> lookingOptions; 7 -> interestOptions; 8 -> goalOptions; else -> emptyList() }
    AuroraBackground { Column(Modifier.fillMaxSize().padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { if (step > 0) IconButton({ step-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.previous_step)) } else Spacer(Modifier.width(48.dp)); LinearProgressIndicator(progress = { (step + 1) / 9f }, Modifier.weight(1f).height(6.dp).clip(CircleShape), color = Pink, trackColor = Color(0xFF2B2333)); Spacer(Modifier.width(48.dp)) }
        Spacer(Modifier.height(38.dp)); Text(titles[step], style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(8.dp)); Text(subtitles[step], color = Muted); Spacer(Modifier.height(24.dp))
        when (step) {
            0 -> DarkField(name, { name = it }, stringResource(R.string.your_name))
            1 -> DarkField(birth, { birth = it }, stringResource(R.string.birth_date_hint))
            4 -> Column(Modifier.verticalScroll(rememberScrollState())) { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.location_permission_title), fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.location_permission_body), color = Muted, style = MaterialTheme.typography.bodySmall) }; Spacer(Modifier.height(12.dp)); GlowButton(stringResource(R.string.allow_location), { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }, Modifier.fillMaxWidth(), Icons.Default.LocationOn, enabled = !locating && locationProvider != null); locationNotice?.let { Text(stringResource(it), color = Pink, modifier = Modifier.padding(top = 10.dp)) }; location?.let { Text(it.city, color = Color(0xFF55E6A5), modifier = Modifier.padding(top = 10.dp)) }; Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.choose_city_manually), color = Muted); Spacer(Modifier.height(8.dp)); DarkField(country, { country = it; location = null }, stringResource(R.string.country_optional)); Spacer(Modifier.height(10.dp)); DarkField(city, { city = it; location = null }, stringResource(R.string.city)) }
            5 -> Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(3) { position -> SurfaceCard(Modifier.weight(1f).aspectRatio(.72f).clickable { pendingPosition = position; picker.launch("image/*") }, padding = 0.dp) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(if (position in uploadedPositions) Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate, null, tint = if (position in uploadedPositions) Color(0xFF55E6A5) else Pink, modifier = Modifier.size(38.dp)); Spacer(Modifier.height(8.dp)); Text(if (position in uploadedPositions) stringResource(R.string.added) else stringResource(R.string.photo_number, position + 1), style = MaterialTheme.typography.labelMedium) } } } } }; Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.photos_hint), color = Muted) }
            6 -> OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth().height(160.dp), label = { Text(stringResource(R.string.about_you)) }, shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Glass, unfocusedContainerColor = Glass, focusedBorderColor = Pink, unfocusedBorderColor = Color(0xFF3A3342)))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(options) { (labelId, value) -> val selected = when (step) { 2 -> gender == value; 3 -> lookingFor == value; 7 -> value in selectedInterests; 8 -> goal == value; else -> false }; SurfaceCard(Modifier.fillMaxWidth().clickable { when (step) { 2 -> gender = value; 3 -> lookingFor = value; 7 -> selectedInterests = if (selected) selectedInterests - value else selectedInterests + value; 8 -> goal = value } }, padding = 18.dp) { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(labelId), Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Icon(if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (selected) Pink else Muted) } } } }
        }
        Spacer(Modifier.weight(1f)); val valid = when (step) { 0 -> name.isNotBlank(); 1 -> Regex("\\d{4}-\\d{2}-\\d{2}").matches(birth); 4 -> city.isNotBlank(); 5 -> uploadedPositions.isNotEmpty(); 7 -> selectedInterests.size >= 3; else -> true }
        GlowButton(if (step == 8) stringResource(R.string.enter_app, stringResource(R.string.app_name)) else stringResource(R.string.continue_action), { if (valid) { if (step < 8) step++ else { val genders = if (lookingFor == "everyone") listOf("woman", "man", "non_binary", "other") else listOf(lookingFor); val resolved = location ?: ProfileLocation(country = country.ifBlank { null }, city = city, locationSource = "manual"); val draft = ProfileDraft(name, birth, gender, city, bio.ifBlank { context.getString(R.string.default_bio) }, goal); if (onDoneWithLocation != null) onDoneWithLocation(draft, selectedInterests.toList(), genders, resolved) else onDone(draft, selectedInterests.toList(), genders) } } }, Modifier.fillMaxWidth())
        if (!valid) { Spacer(Modifier.height(8.dp)); Text(stringResource(when (step) { 5 -> R.string.photo_required; 7 -> R.string.interests_required; else -> R.string.field_required }), color = Pink, modifier = Modifier.align(Alignment.CenterHorizontally)) }; Spacer(Modifier.height(18.dp))
    } }
}

@Composable
fun PersonPhoto(person: Person, modifier: Modifier) {
    val description = stringResource(R.string.person_profile_photo, person.name)
    if (person.photoUrl != null) AsyncImage(person.photoUrl, description, modifier, contentScale = ContentScale.Crop)
    else Image(painterResource(person.image), description, modifier, contentScale = ContentScale.Crop)
}

private fun avatarPerson(id: String, name: String, photoUrl: String?) = Person(id, name, 18, "", null, "", R.drawable.maya, photoUrl = photoUrl)

@Composable
private fun distanceText(distanceKm: Int?): String = when {
    distanceKm == null -> stringResource(R.string.distance_nearby)
    distanceKm <= 1 -> stringResource(R.string.distance_under_two)
    else -> stringResource(R.string.distance_approx, distanceKm)
}

@Composable
fun DiscoverConnected(person: Person, onDetail: () -> Unit, onSkip: () -> Unit, onLike: () -> Unit, onSuper: () -> Unit, onRewind: (() -> Unit)? = null) {
    var offset by remember(person.id) { mutableFloatStateOf(0f) }
    AuroraBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) { BrandMark(38.dp); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.nav_discover), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f)); if (onRewind != null) IconButton(onRewind) { Icon(Icons.Default.Replay, stringResource(R.string.rewind_last)) } }
            Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { rotationZ = offset / 42; translationX = offset }.pointerInput(person.id) { detectDragGestures(onDragEnd = { when { offset > 180 -> onLike(); offset < -180 -> onSkip() }; offset = 0f }) { change, drag -> change.consume(); offset += drag.x } }.clip(RoundedCornerShape(30.dp)).clickable(onClick = onDetail)) {
                PersonPhoto(person, Modifier.fillMaxSize()); Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color(0xEE09070D)))))
                Row(Modifier.align(Alignment.TopStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).background(if (person.online) Color(0xFF55E6A5) else Muted, CircleShape)); Spacer(Modifier.width(7.dp)); Text(stringResource(if (person.online) R.string.online else R.string.recently_active), style = MaterialTheme.typography.labelLarge) }
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) { Row(verticalAlignment = Alignment.Bottom) { Text("${person.name}, ${person.age}", style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Verified, stringResource(R.string.verified_profile), tint = Violet, modifier = Modifier.size(22.dp)) }; Text(stringResource(R.string.profile_place_distance, person.city, distanceText(person.distanceKm)), color = Color.White.copy(.8f)); if (person.interests.isNotEmpty()) { Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { person.interests.take(3).forEach { interest -> AssistChip({}, { Text(localizedInterest(interest)) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color(0x99211829)), border = null) } } } }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Action(Icons.Default.Close, Color(0xFF9B93A5), 54.dp, stringResource(R.string.pass), onSkip); Action(Icons.Default.Star, Violet, 62.dp, stringResource(R.string.super_like), onSuper); Action(Icons.Default.Favorite, Pink, 72.dp, stringResource(R.string.like), onLike) }
        }
    }
}

@Composable
fun DiscoverEmpty(loaded: Boolean, premium: Boolean, onRefresh: () -> Unit, onSettings: () -> Unit, onRewind: () -> Unit) = AuroraBackground {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!loaded) { CircularProgressIndicator(color = Pink); Spacer(Modifier.height(18.dp)); Text(stringResource(R.string.finding_nearby), color = Muted) }
        else { BrandMark(72.dp); Spacer(Modifier.height(22.dp)); Text(stringResource(R.string.caught_up), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center); Spacer(Modifier.height(10.dp)); Text(stringResource(R.string.caught_up_hint), color = Muted, textAlign = TextAlign.Center); Spacer(Modifier.height(26.dp)); GlowButton(stringResource(R.string.refresh), onRefresh, Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)); OutlinedButton(onSettings, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) { Icon(Icons.Outlined.Tune, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.adjust_filters)) }; if (premium) { Spacer(Modifier.height(10.dp)); TextButton(onRewind) { Icon(Icons.Default.Replay, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rewind_last)) } } }
    }
}

@Composable
fun ProfileDetailConnected(person: Person, onBack: () -> Unit, onLike: () -> Unit, onSuper: () -> Unit, onBlock: () -> Unit, onReport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }; var confirmBlock by remember { mutableStateOf(false) }
    AuroraBackground { Box {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Box(Modifier.fillMaxWidth().height(500.dp)) { val urls = person.photoUrls.ifEmpty { listOfNotNull(person.photoUrl) }; if (urls.isEmpty()) PersonPhoto(person, Modifier.fillMaxSize()) else androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxSize()) { items(urls.size) { index -> AsyncImage(urls[index], stringResource(R.string.profile_photo_number, index + 1), Modifier.fillParentMaxWidth().fillMaxHeight(), contentScale = ContentScale.Crop) } }; Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink)))); Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) { IconButton(onBack, Modifier.background(Color(0x77211B2B), CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }; Spacer(Modifier.weight(1f)); Box { IconButton({ menu = true }, Modifier.background(Color(0x77211B2B), CircleShape)) { Icon(Icons.Default.MoreVert, stringResource(R.string.safety_actions)) }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text(stringResource(R.string.report_profile)) }, { menu = false; onReport() }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }); DropdownMenuItem({ Text(stringResource(R.string.block_profile)) }, { menu = false; confirmBlock = true }, leadingIcon = { Icon(Icons.Outlined.Block, null) }) } } }; Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) { Text("${person.name}, ${person.age}", style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.profile_place_distance, person.city, distanceText(person.distanceKm)), color = Muted); if (urls.size > 1) Text(stringResource(R.string.swipe_photos, urls.size), color = Muted, style = MaterialTheme.typography.labelMedium) } } }
            item { Column(Modifier.padding(horizontal = 24.dp)) { Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); Text(person.bio, color = Color.White.copy(.84f)); if (person.interests.isNotEmpty()) { Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.interests), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(10.dp)); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { person.interests.forEach { SurfaceCard(padding = 10.dp) { Text(localizedInterest(it)) } } } }; Spacer(Modifier.height(24.dp)); SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.looking_for), color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(6.dp)); Text(localizedGoal(person.goal), fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(120.dp)) } }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))).padding(24.dp)) { Action(Icons.Default.Star, Violet, 58.dp, stringResource(R.string.super_like), onSuper); Spacer(Modifier.width(20.dp)); GlowButton(stringResource(R.string.like_person, person.name), onLike, Modifier.weight(1f), Icons.Default.Favorite) }
        if (confirmBlock) AlertDialog({ confirmBlock = false }, title = { Text(stringResource(R.string.block_person_title, person.name)) }, text = { Text(stringResource(R.string.block_person_body, person.name)) }, confirmButton = { TextButton({ confirmBlock = false; onBlock() }) { Text(stringResource(R.string.block), color = Pink) } }, dismissButton = { TextButton({ confirmBlock = false }) { Text(stringResource(R.string.cancel)) } })
    } }
}

@Composable
fun MatchConnected(me: MyProfile?, person: DiscoverProfile?, onChat: () -> Unit, onContinue: () -> Unit) = Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF522146), Ink), radius = 1100f))) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.match_prefix), style = MaterialTheme.typography.displayLarge); Text(stringResource(R.string.match_word), color = Pink, style = MaterialTheme.typography.displayLarge); Text(stringResource(R.string.match_message, person?.name ?: stringResource(R.string.someone_special)), color = Muted, textAlign = TextAlign.Center); Spacer(Modifier.height(42.dp)); Box(Modifier.height(230.dp).fillMaxWidth()) { MatchPortrait(me?.photoUrl, stringResource(R.string.your_photo), Violet, Modifier.align(Alignment.CenterStart).offset(x = 32.dp).rotate(-8f)); MatchPortrait(person?.photoUrl, person?.name ?: stringResource(R.string.match_word), Pink, Modifier.align(Alignment.CenterEnd).offset(x = (-32).dp).rotate(8f)); BrandMark(58.dp) }; Spacer(Modifier.height(44.dp)); GlowButton(stringResource(R.string.send_message), onChat, Modifier.fillMaxWidth(), Icons.AutoMirrored.Filled.Chat); TextButton(onContinue) { Text(stringResource(R.string.keep_discovering), color = White) }
    }
}

@Composable
private fun MatchPortrait(url: String?, description: String, border: Color, modifier: Modifier) { val shape = RoundedCornerShape(30.dp); val styled = modifier.size(190.dp).clip(shape).border(3.dp, border, shape); if (url != null) AsyncImage(url, description, styled, contentScale = ContentScale.Crop) else Box(styled.background(Brush.linearGradient(listOf(Color(0xFF392743), Color(0xFF17131E)))), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, description, tint = Muted, modifier = Modifier.size(72.dp)) } }

@Composable
fun MatchesConnected(matches: List<MatchSummary>, conversations: List<Conversation>, onChat: (String?) -> Unit) = AuroraBackground {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.nav_matches), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.connections_potential), color = Muted) }
        if (matches.isEmpty()) item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.no_matches), fontWeight = FontWeight.Bold); Text(stringResource(R.string.mutual_like_hint), color = Muted) } }
        else items(matches, key = { it.id }) { match -> SurfaceCard(Modifier.fillMaxWidth().clickable { onChat(conversations.firstOrNull { it.matchId == match.id }?.id) }) { Row(verticalAlignment = Alignment.CenterVertically) { PersonPhoto(avatarPerson(match.id, match.name, match.photoUrl), Modifier.size(64.dp).clip(CircleShape)); Spacer(Modifier.width(12.dp)); Text(match.name, fontWeight = FontWeight.Bold) } } }
        if (conversations.isEmpty()) item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.no_conversations), fontWeight = FontWeight.Bold); Text(stringResource(R.string.conversation_hint), color = Muted) } }
    }
}

@Composable
fun ChatsConnected(conversations: List<Conversation>, onChat: (String?) -> Unit) = AuroraBackground {
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.nav_chats), style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(12.dp)); DarkField("", {}, stringResource(R.string.search_conversations)) }
        if (conversations.isEmpty()) item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.conversations_empty), fontWeight = FontWeight.Bold); Text(stringResource(R.string.chat_match_hint), color = Muted) } }
        else items(conversations, key = { it.id }) { conversation -> SurfaceCard(Modifier.fillMaxWidth().clickable { onChat(conversation.id) }) { Row(verticalAlignment = Alignment.CenterVertically) { PersonPhoto(avatarPerson(conversation.id, conversation.name, conversation.photoUrl), Modifier.size(60.dp).clip(CircleShape)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(conversation.name, fontWeight = FontWeight.Bold); Text(conversation.lastMessage.orEmpty(), color = Muted, maxLines = 1) }; if (conversation.unreadCount > 0) Badge { Text(conversation.unreadCount.toString()) } } } }
    }
}

@Composable
fun ChatConnected(messages: List<ChatMessage>, myId: String?, conversation: Conversation?, typing: Boolean, peerOnline: Boolean, onBack: () -> Unit, onSend: (String, String?) -> Unit, onPhoto: (ByteArray, String, String?) -> Unit, onTyping: (Boolean) -> Unit, onUnmatch: () -> Unit, onBlock: () -> Unit, onReport: () -> Unit) {
    var text by remember { mutableStateOf("") }; var menu by remember { mutableStateOf(false) }; var confirmation by remember { mutableStateOf<String?>(null) }; var reply by remember { mutableStateOf<ChatMessage?>(null) }; val context = LocalContext.current; val scope = rememberCoroutineScope(); val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch(Dispatchers.IO) { val mime = context.contentResolver.getType(uri) ?: "image/jpeg"; context.contentResolver.openInputStream(uri)?.use { onPhoto(it.readBytes(), mime, reply?.id) }; reply = null } }
    AuroraBackground { Column(Modifier.fillMaxSize()) {
        Top(conversation?.name ?: stringResource(R.string.chat), onBack) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(if (peerOnline) Color(0xFF55E6A5) else Muted, CircleShape)); Spacer(Modifier.width(6.dp)); Text(stringResource(if (typing) R.string.typing else if (peerOnline) R.string.online_lower else R.string.recently_active_lower), color = Muted); Box { IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.conversation_actions)) }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text(stringResource(R.string.report)) }, { menu = false; onReport() }, leadingIcon = { Icon(Icons.Outlined.Flag, null) }); DropdownMenuItem({ Text(stringResource(R.string.unmatch)) }, { menu = false; confirmation = "unmatch" }, leadingIcon = { Icon(Icons.Outlined.HeartBroken, null) }); DropdownMenuItem({ Text(stringResource(R.string.block)) }, { menu = false; confirmation = "block" }, leadingIcon = { Icon(Icons.Outlined.Block, null) }) } } } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { if (messages.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.WavingHand, null, tint = Violet, modifier = Modifier.size(38.dp)); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.start_from_profile), color = Muted, textAlign = TextAlign.Center) } }; items(messages, key = { it.id }) { message -> MessageBubble(message, message.senderId == myId, messages.firstOrNull { it.id == message.replyToId }) { reply = message } } }
        reply?.let { replying -> Row(Modifier.fillMaxWidth().background(Glass).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Violet); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.replying), color = Violet, style = MaterialTheme.typography.labelLarge); Text(replying.body ?: stringResource(R.string.photo), maxLines = 1) }; IconButton({ reply = null }) { Icon(Icons.Default.Close, stringResource(R.string.cancel_reply)) } } }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton({ picker.launch("image/*") }) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.attach_photo), tint = Muted) }; IconButton({ text += "😊"; onTyping(true) }) { Icon(Icons.Outlined.EmojiEmotions, stringResource(R.string.add_emoji), tint = Muted) }; OutlinedTextField(text, { text = it; onTyping(it.isNotBlank()) }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.message_hint)) }, shape = RoundedCornerShape(24.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Glass, focusedContainerColor = Glass, unfocusedBorderColor = Color.Transparent, focusedBorderColor = Pink)); IconButton({ val value = text; if (value.isNotBlank()) { text = ""; onTyping(false); onSend(value, reply?.id); reply = null } }, Modifier.padding(start = 8.dp).background(Pink, CircleShape)) { Icon(Icons.Default.ArrowUpward, stringResource(R.string.send)) } }
    }; confirmation?.let { action -> AlertDialog({ confirmation = null }, title = { Text(stringResource(if (action == "block") R.string.block_chat_title else R.string.unmatch_title)) }, text = { Text(stringResource(if (action == "block") R.string.block_chat_body else R.string.unmatch_body)) }, confirmButton = { TextButton({ confirmation = null; if (action == "block") onBlock() else onUnmatch() }) { Text(stringResource(if (action == "block") R.string.block else R.string.unmatch), color = Pink) } }, dismissButton = { TextButton({ confirmation = null }) { Text(stringResource(R.string.cancel)) } }) } }
}

@Composable
fun MessageBubble(message: ChatMessage, mine: Boolean, replied: ChatMessage?, onReply: () -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) { SurfaceCard(Modifier.widthIn(max = 290.dp), padding = 13.dp) { if (replied != null) { Text(replied.body ?: stringResource(R.string.photo), color = Violet, style = MaterialTheme.typography.bodySmall, maxLines = 2); Spacer(Modifier.height(7.dp)) }; if (message.mediaUrl != null) AsyncImage(message.mediaUrl, stringResource(R.string.shared_photo), Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop); message.body?.let { if (message.mediaUrl != null) Spacer(Modifier.height(8.dp)); Text(it) } }; Row(verticalAlignment = Alignment.CenterVertically) { TextButton(onReply, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Icon(Icons.AutoMirrored.Filled.Reply, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(3.dp)); Text(stringResource(R.string.reply), style = MaterialTheme.typography.labelSmall) }; if (mine && message.readAt != null) Text(stringResource(R.string.read), color = Muted, style = MaterialTheme.typography.labelSmall) } }
}

@Composable
fun MyProfileConnected(
    me: MyProfile?,
    photos: List<ProfilePhoto>,
    onSettings: () -> Unit,
    onPremium: () -> Unit,
    onSave: (ProfileDraft, List<String>) -> Unit,
    onPhoto: (Int, ByteArray, String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    location: ProfileLocation? = null,
    socialProfiles: List<SocialProfile> = emptyList(),
) {
    var edit by remember { mutableStateOf(false) }; var pendingPosition by remember { mutableIntStateOf(0) }; val context = LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) { val mime = context.contentResolver.getType(uri) ?: "image/jpeg"; val position = pendingPosition; scope.launch(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { onPhoto(position, it.readBytes(), mime) } } } }
    AuroraBackground { LazyColumn(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.my_profile), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f)); IconButton(onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) } } }
        item { val primary = photos.firstOrNull { it.position == 0 }?.url ?: me?.photoUrl; Box(Modifier.clickable { pendingPosition = 0; picker.launch("image/*") }) { val avatar = Modifier.size(144.dp).clip(CircleShape).border(3.dp, Brush.linearGradient(listOf(Pink, Violet)), CircleShape); if (primary != null) AsyncImage(primary, stringResource(R.string.your_profile_photo), avatar, contentScale = ContentScale.Crop) else Box(avatar.background(Brush.linearGradient(listOf(Color(0xFF32233E), Color(0xFF17131E)))), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, stringResource(R.string.add_profile_photo), tint = Muted, modifier = Modifier.size(64.dp)) }; Box(Modifier.align(Alignment.BottomEnd).size(38.dp).background(Pink, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Edit, stringResource(R.string.edit), modifier = Modifier.size(20.dp)) } }; Text(me?.name ?: stringResource(R.string.your_profile), style = MaterialTheme.typography.headlineMedium); Text(location?.city ?: me?.city ?: stringResource(R.string.add_city), color = Muted) }
        item { Column(Modifier.fillMaxWidth()) { Text(stringResource(R.string.photos), color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(6) { position -> val photo = photos.firstOrNull { it.position == position }; Box(Modifier.size(104.dp, 138.dp).clip(RoundedCornerShape(18.dp)).background(Glass).border(1.dp, Color(0x334F405E), RoundedCornerShape(18.dp)).clickable { pendingPosition = position; picker.launch("image/*") }, contentAlignment = Alignment.Center) { if (photo != null) { AsyncImage(photo.url, stringResource(R.string.profile_photo_number, position + 1), Modifier.fillMaxSize(), contentScale = ContentScale.Crop); IconButton({ onDeletePhoto(photo.id) }, Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).background(Color(0xAA0A0710), CircleShape)) { Icon(Icons.Default.Close, stringResource(R.string.delete_photo), modifier = Modifier.size(16.dp)) } } else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AddPhotoAlternate, null, tint = Muted); Text(stringResource(R.string.add), color = Muted, style = MaterialTheme.typography.labelSmall) } } } } } }
        item { SurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onPremium)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Pink); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.premium_name, stringResource(R.string.app_name)), fontWeight = FontWeight.Bold); Text(stringResource(R.string.see_likes_rewind), color = Muted) }; Icon(Icons.Default.ChevronRight, null) } } }
        socialProfiles.firstOrNull { it.provider == "instagram" && it.visibleInProfile }?.let { instagram -> item { SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AlternateEmail, null, tint = Pink); Spacer(Modifier.width(10.dp)); Text("@${instagram.username}", fontWeight = FontWeight.SemiBold) } } } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.your_story), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f)); TextButton({ edit = true }) { Text(stringResource(R.string.edit)) } } }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_label), color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); val bioText = me?.bio.orEmpty(); Text(if (bioText.isBlank()) stringResource(R.string.add_bio) else bioText); if (!me?.interests.isNullOrEmpty()) { Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.interests_label), color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); val localized = me!!.interests.map { localizedInterest(it) }; Text(localized.joinToString("  •  "), color = Muted) }; Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.looking_for), color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Text(localizedGoal(me?.datingGoal ?: "relationship"), color = Muted) } }
        item { Spacer(Modifier.height(18.dp)) }
    }
        if (edit) ProfileEditDialog(me, { edit = false }, { profile, interests -> edit = false; onSave(profile, interests) })
    }
}

@Composable
private fun ProfileEditDialog(me: MyProfile?, onDismiss: () -> Unit, onSave: (ProfileDraft, List<String>) -> Unit) {
    var name by remember { mutableStateOf(me?.name.orEmpty()) }; var city by remember { mutableStateOf(me?.city.orEmpty()) }; var bio by remember { mutableStateOf(me?.bio.orEmpty()) }; var goal by remember { mutableStateOf(me?.datingGoal ?: "relationship") }; var selected by remember { mutableStateOf(me?.interests.orEmpty().toSet()) }
    val interests = listOf(R.string.interest_travel to "Travel", R.string.interest_coffee to "Coffee", R.string.interest_design to "Design", R.string.interest_live_music to "Live music", R.string.interest_hiking to "Hiking", R.string.interest_cooking to "Cooking", R.string.interest_photography to "Photography", R.string.interest_books to "Books")
    val goals = listOf(R.string.goal_relationship to "relationship", R.string.goal_conversations to "communication", R.string.goal_dating to "dating", R.string.goal_friendship to "friendship", R.string.goal_unknown to "unknown")
    AlertDialog(onDismiss, title = { Text(stringResource(R.string.edit_profile)) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { DarkField(name, { name = it }, stringResource(R.string.name)); DarkField(city, { city = it }, stringResource(R.string.city)); OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.bio)) }); Text(stringResource(R.string.dating_goal), color = Muted); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { goals.forEach { (label, value) -> FilterChip(goal == value, { goal = value }, { Text(stringResource(label)) }) } }; Text(stringResource(R.string.choose_three_interests), color = Muted); FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { interests.forEach { (label, value) -> FilterChip(value in selected, { selected = if (value in selected) selected - value else selected + value }, { Text(stringResource(label)) }) } } } }, confirmButton = { TextButton({ if (selected.size >= 3 && name.isNotBlank() && city.isNotBlank()) onSave(ProfileDraft(name, me?.birthDate?.take(10) ?: "1997-04-12", me?.gender ?: "man", city, bio, goal), selected.toList()) }) { Text(stringResource(R.string.save), color = if (selected.size >= 3 && name.isNotBlank() && city.isNotBlank()) Pink else Muted) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun providerLabel(provider: String): String = stringResource(when (provider) { "email" -> R.string.provider_email; "google" -> R.string.provider_google; "phone" -> R.string.provider_phone; "x" -> R.string.provider_x; "vk" -> R.string.provider_vk; else -> R.string.provider_instagram })

@Composable
private fun AccountRow(provider: String, identity: AuthIdentity?, available: Boolean, onConnect: () -> Unit, onUnlink: () -> Unit) {
    SurfaceCard(Modifier.fillMaxWidth(), padding = 14.dp) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(when (provider) { "phone" -> Icons.Default.Phone; "x", "instagram" -> Icons.Default.AlternateEmail; "vk" -> Icons.Default.Public; else -> Icons.Default.AccountCircle }, null, tint = if (identity != null) Color(0xFF55E6A5) else Muted); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(providerLabel(provider), fontWeight = FontWeight.SemiBold); Text(identity?.maskedEmail ?: identity?.maskedPhone ?: stringResource(if (identity != null) R.string.connected else if (available) R.string.connect else R.string.not_configured), color = Muted, style = MaterialTheme.typography.bodySmall) }; if (identity != null) TextButton(onUnlink) { Text(stringResource(R.string.unlink), color = Pink) } else if (available) TextButton(onConnect) { Text(stringResource(R.string.connect), color = Pink) } } }
}

@Composable
fun SettingsConnected(
    initial: SearchPreferences,
    initialHidden: Boolean,
    blocked: List<BlockedUser>,
    onBack: () -> Unit,
    onSave: (SearchPreferences) -> Unit,
    onHidden: (Boolean) -> Unit,
    onUnblock: (String) -> Unit,
    onLogout: () -> Unit,
    onDelete: () -> Unit,
    providers: AuthProviders = AuthProviders(),
    identities: List<AuthIdentity> = emptyList(),
    location: ProfileLocation? = null,
    socialProfiles: List<SocialProfile> = emptyList(),
    googleConfigured: Boolean = false,
    vkConfigured: Boolean = false,
    onLanguage: (AppLanguage) -> Unit = {},
    onDeviceLocation: () -> Unit = {},
    onManualLocation: (String?, String) -> Unit = { _, _ -> },
    onGoogle: () -> Unit = {},
    onPhone: () -> Unit = {},
    onX: () -> Unit = {},
    onVk: () -> Unit = {},
    onUnlink: (String) -> Unit = {},
    onSaveInstagram: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveInstagram: () -> Unit = {},
) {
    var minAge by remember(initial) { mutableFloatStateOf(initial.minAge.toFloat()) }; var maxAge by remember(initial) { mutableFloatStateOf(initial.maxAge.toFloat()) }; var distance by remember(initial) { mutableFloatStateOf(initial.maxDistanceKm.toFloat()) }; var notifications by remember(initial) { mutableStateOf(initial.notifications) }; var showGenders by remember(initial) { mutableStateOf(initial.showGenders.toSet()) }; var hidden by remember(initialHidden) { mutableStateOf(initialHidden) }; var deleteDialog by remember { mutableStateOf(false) }; var manualDialog by remember { mutableStateOf(false) }; var instagramDialog by remember { mutableStateOf(false) }; var unlinkProvider by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants -> if (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true || grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) onDeviceLocation() else manualDialog = true }
    val genders = listOf("woman" to R.string.show_women, "man" to R.string.show_men, "non_binary" to R.string.gender_non_binary, "other" to R.string.show_other)
    val instagram = socialProfiles.firstOrNull { it.provider == "instagram" }
    AuroraBackground { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Top(stringResource(R.string.settings), onBack) }
        item { SettingsHeader(stringResource(R.string.language)) }
        item { SurfaceCard(Modifier.fillMaxWidth()) { listOf(AppLanguage.System to R.string.language_system, AppLanguage.Russian to R.string.language_russian, AppLanguage.English to R.string.language_english).forEach { (language, label) -> Row(Modifier.fillMaxWidth().clickable { onLanguage(language) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(LocaleController.current() == language, { onLanguage(language) }); Text(stringResource(label)) } } } }
        item { SettingsHeader(stringResource(R.string.location_section)) }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, tint = Pink); Spacer(Modifier.width(10.dp)); Column { Text(location?.city ?: stringResource(R.string.location_not_set), fontWeight = FontWeight.SemiBold); Text(stringResource(if (location?.locationSource == "gps") R.string.location_source_gps else R.string.location_source_manual), color = Muted, style = MaterialTheme.typography.bodySmall) } }; Spacer(Modifier.height(12.dp)); OutlinedButton({ permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_location)) }; TextButton({ manualDialog = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.choose_city_manually), color = Pink) } } }
        item { SettingsHeader(stringResource(R.string.connected_accounts)) }
        item { AccountRow("email", identities.firstOrNull { it.provider == "email" }, false, {}, { unlinkProvider = "email" }) }
        item { AccountRow("google", identities.firstOrNull { it.provider == "google" }, providers.google && googleConfigured, onGoogle, { unlinkProvider = "google" }) }
        item { AccountRow("phone", identities.firstOrNull { it.provider == "phone" }, providers.phone, onPhone, { unlinkProvider = "phone" }) }
        item { AccountRow("x", identities.firstOrNull { it.provider == "x" }, providers.x, onX, { unlinkProvider = "x" }) }
        item { AccountRow("vk", identities.firstOrNull { it.provider == "vk" }, providers.vk && vkConfigured, onVk, { unlinkProvider = "vk" }) }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AlternateEmail, null, tint = if (instagram != null) Pink else Muted); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.provider_instagram), fontWeight = FontWeight.SemiBold); Text(instagram?.let { "@${it.username}" } ?: stringResource(R.string.instagram_no_password), color = Muted, style = MaterialTheme.typography.bodySmall) }; TextButton({ instagramDialog = true }) { Text(stringResource(if (instagram == null) R.string.add else R.string.edit), color = Pink) }; if (instagram != null) IconButton(onRemoveInstagram) { Icon(Icons.Default.Close, stringResource(R.string.remove)) } } } }
        item { SettingsHeader(stringResource(R.string.settings_discovery)) }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.age_range, minAge.toInt(), maxAge.toInt()), fontWeight = FontWeight.SemiBold); RangeSlider(value = minAge..maxAge, onValueChange = { minAge = it.start; maxAge = it.endInclusive }, valueRange = 18f..80f) } }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.max_distance, distance.toInt()), fontWeight = FontWeight.SemiBold); Slider(distance, { distance = it }, valueRange = 5f..200f) } }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.show_me), fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(10.dp)); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { genders.forEach { (value, label) -> FilterChip(value in showGenders, { showGenders = if (value in showGenders && showGenders.size > 1) showGenders - value else showGenders + value }, { Text(stringResource(label)) }) } } } }
        item { GlowButton(stringResource(R.string.save_discovery), { onSave(SearchPreferences(minAge.toInt(), maxAge.toInt(), distance.toInt(), showGenders.toList(), notifications)) }, Modifier.fillMaxWidth()) }
        item { SettingsHeader(stringResource(R.string.privacy_safety)) }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.notifications), Modifier.weight(1f)); Switch(notifications, { notifications = it }) } } }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.hide_profile)); Text(stringResource(R.string.hide_profile_hint), color = Muted, style = MaterialTheme.typography.bodyMedium) }; Switch(hidden, { hidden = it; onHidden(it) }) } } }
        if (blocked.isNotEmpty()) { item { Text(stringResource(R.string.blocked_profiles), style = MaterialTheme.typography.titleMedium) }; items(blocked, key = { it.id }) { person -> SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(person.name ?: stringResource(R.string.blocked_member), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.blocked_hint), color = Muted, style = MaterialTheme.typography.bodySmall) }; TextButton({ onUnblock(person.id) }) { Text(stringResource(R.string.unblock), color = Pink) } } } } }
        item { SettingsHeader(stringResource(R.string.account)) }
        item { TextButton(onLogout, Modifier.fillMaxWidth()) { Text(stringResource(R.string.sign_out), color = Pink) } }
        item { TextButton({ deleteDialog = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.delete_account), color = Muted) }; Spacer(Modifier.height(24.dp)) }
    }
        if (manualDialog) ManualLocationDialog({ manualDialog = false }) { country, city -> manualDialog = false; onManualLocation(country, city) }
        if (instagramDialog) InstagramDialog(instagram, { instagramDialog = false }) { username, visible -> instagramDialog = false; onSaveInstagram(username, visible) }
        unlinkProvider?.let { provider -> AlertDialog({ unlinkProvider = null }, title = { Text(stringResource(R.string.unlink_title, providerLabel(provider))) }, text = { Text(stringResource(R.string.unlink_body)) }, confirmButton = { TextButton({ unlinkProvider = null; onUnlink(provider) }) { Text(stringResource(R.string.unlink), color = Pink) } }, dismissButton = { TextButton({ unlinkProvider = null }) { Text(stringResource(R.string.cancel)) } }) }
        if (deleteDialog) AlertDialog({ deleteDialog = false }, title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_body)) }, confirmButton = { TextButton({ deleteDialog = false; onDelete() }) { Text(stringResource(R.string.delete_permanently), color = Pink) } }, dismissButton = { TextButton({ deleteDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun ManualLocationDialog(onDismiss: () -> Unit, onSave: (String?, String) -> Unit) { var country by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }; AlertDialog(onDismiss, title = { Text(stringResource(R.string.manual_location_title)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DarkField(country, { country = it }, stringResource(R.string.country_optional)); DarkField(city, { city = it }, stringResource(R.string.city)) } }, confirmButton = { TextButton({ if (city.isNotBlank()) onSave(country.ifBlank { null }, city) }) { Text(stringResource(R.string.save), color = if (city.isNotBlank()) Pink else Muted) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }) }

@Composable
private fun InstagramDialog(current: SocialProfile?, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) { var username by remember { mutableStateOf(current?.username.orEmpty()) }; var visible by remember { mutableStateOf(current?.visibleInProfile ?: true) }; AlertDialog(onDismiss, title = { Text(stringResource(if (current == null) R.string.instagram_add else R.string.instagram_edit)) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DarkField(username, { username = it }, stringResource(R.string.instagram_handle)); Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.instagram_visible), Modifier.weight(1f)); Switch(visible, { visible = it }) }; Text(stringResource(R.string.instagram_no_password), color = Muted, style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton({ if (username.isNotBlank()) onSave(username, visible) }) { Text(stringResource(R.string.save), color = if (username.isNotBlank()) Pink else Muted) } }, dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }) }

@Composable
fun PremiumConnected(subscription: Subscription, likes: ReceivedLikes, onBack: () -> Unit, onActivate: () -> Unit, onBoost: () -> Unit) = Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF261530), Ink)))) {
    val benefits = listOf(Triple(Icons.Default.Replay, R.string.rewind, R.string.rewind_detail), Triple(Icons.Default.Bolt, R.string.boost, R.string.boost_detail), Triple(Icons.Default.Star, R.string.more_super_likes, R.string.super_likes_detail))
    LazyColumn(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth()) { IconButton(onBack) { Icon(Icons.Default.Close, stringResource(R.string.close)) } }; BrandMark(68.dp); Spacer(Modifier.height(12.dp)); Text(if (subscription.premium) stringResource(R.string.premium_name, stringResource(R.string.app_name).uppercase()) else stringResource(R.string.app_name).uppercase(), color = Pink, style = MaterialTheme.typography.headlineLarge); Text(stringResource(if (subscription.premium) R.string.premium_active else R.string.premium_pitch), color = Muted, textAlign = TextAlign.Center) }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.today), color = Pink, style = MaterialTheme.typography.labelLarge); Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.actions_usage, subscription.actionsUsed, subscription.actionLimit)); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { if (subscription.actionLimit == 0) 0f else subscription.actionsUsed.toFloat() / subscription.actionLimit }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Pink, trackColor = Color(0xFF30263A)); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.super_likes_usage, subscription.superLikesUsed, subscription.superLikeLimit), color = Muted) } }
        item { SurfaceCard(Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Favorite, null, tint = Pink); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(stringResource(R.string.people_like_you), fontWeight = FontWeight.Bold); Text(stringResource(if (likes.locked) R.string.likes_waiting else R.string.likes_interested, likes.count), color = Muted) } } } }
        items(benefits) { (icon, title, subtitle) -> Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).background(Color(0x332E1656), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Pink) }; Spacer(Modifier.width(12.dp)); Column { Text(stringResource(title), fontWeight = FontWeight.Bold); Text(stringResource(subtitle), color = Muted) } } }
        if (subscription.premium) item { GlowButton(stringResource(R.string.activate_boost), onBoost, Modifier.fillMaxWidth(), Icons.Default.Bolt); subscription.validUntil?.let { Text(stringResource(R.string.premium_through, it.take(10)), color = Muted) } } else item { SurfaceCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.development_plan), color = Pink, fontWeight = FontWeight.Bold); Text(stringResource(R.string.development_plan_detail), color = Muted); Spacer(Modifier.height(14.dp)); GlowButton(stringResource(R.string.activate_premium), onActivate, Modifier.fillMaxWidth()) } }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
