@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.velora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import app.velora.ui.theme.*
import app.velora.presentation.*
import app.velora.domain.model.*
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { VeloraTheme { VeloraApp() } } }
}

enum class Screen { Splash, Login, Register, Recover, Onboarding, Discover, Detail, Match, Matches, Chats, Chat, Profile, Settings, Premium }
private val Screen.route get()=name.lowercase()
data class Person(val id:String,val name:String, val age:Int, val city:String, val distance:String, val bio:String, val image:Int, val online:Boolean=true,val photoUrl:String?=null,val interests:List<String> = emptyList(),val goal:String="unknown",val photoUrls:List<String> = emptyList())
fun DiscoverProfile.toPerson()=Person(id,name,age,city,distanceKm?.let{if(it<=1)"< 2 km" else "~$it km"}?:"nearby",bio,if(name.equals("Sofia",true))R.drawable.sofia else R.drawable.maya,online,photoUrl,interests,goal,photoUrls)

@Composable fun VeloraApp(vm:MainViewModel= viewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val nav=rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val current=Screen.entries.firstOrNull{it.route==backStackEntry?.destination?.route}?:Screen.Splash
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1000); splashDone=true }
    fun navigate(target:Screen,root:Boolean=false){nav.navigate(target.route){launchSingleTop=true;restoreState=!root;if(root)popUpTo(Screen.Splash.route){inclusive=true}}}
    LaunchedEffect(splashDone,ui.phase) { if(splashDone){when(ui.phase){SessionPhase.Checking->Unit;SessionPhase.SignedOut->if(current !in listOf(Screen.Login,Screen.Register,Screen.Recover))navigate(Screen.Login,true);SessionPhase.NeedsOnboarding->if(current!=Screen.Onboarding)navigate(Screen.Onboarding,true);SessionPhase.Ready->if(current in listOf(Screen.Splash,Screen.Login,Screen.Register,Screen.Recover,Screen.Onboarding))navigate(Screen.Discover,true)}} }
    LaunchedEffect(ui.pendingMatch?.matchId) { if(ui.pendingMatch!=null&&current!=Screen.Match)navigate(Screen.Match) }
    val go: (Screen)->Unit = { target->if(target!=current)nav.navigate(target.route){popUpTo(Screen.Discover.route){saveState=true};launchSingleTop=true;restoreState=true} }
    val activePerson=ui.discover.firstOrNull()?.toPerson()
    val activeConversation=ui.conversations.firstOrNull{it.id==ui.activeConversationId}
    val openChat: (String?) -> Unit = { id -> val chosen=id?:ui.conversations.firstOrNull()?.id?:ui.pendingMatch?.conversationId;if(chosen!=null){vm.openConversation(chosen);navigate(Screen.Chat)} }
    CompositionLocalProvider(LocalContentColor provides White) { Box(Modifier.fillMaxSize().background(Ink)) {
        NavHost(navController=nav,startDestination=Screen.Splash.route,enterTransition={fadeIn(tween(260))},exitTransition={fadeOut(tween(180))},popEnterTransition={fadeIn(tween(220))},popExitTransition={fadeOut(tween(160))}) {
            composable(Screen.Splash.route){Splash()}
            composable(Screen.Login.route){Login(ui.busy,onCreate={navigate(Screen.Register)},onForgot={navigate(Screen.Recover)},onLogin=vm::login)}
            composable(Screen.Register.route){Register(ui.busy,onBack={nav.popBackStack()},onContinue=vm::register)}
            composable(Screen.Recover.route){Recovery(ui.recoveryToken,onBack={nav.popBackStack()},onRecover=vm::recover,onReset={token,password->vm.resetPassword(token,password)})}
            composable(Screen.Onboarding.route){OnboardingConnected(onDone=vm::completeOnboarding,onPhoto=vm::uploadPhoto)}
            composable(Screen.Discover.route){MainShell(Screen.Discover,go){if(activePerson==null)DiscoverEmpty(ui.homeLoaded,ui.subscription.premium,onRefresh=vm::refresh,onSettings={navigate(Screen.Settings)},onRewind=vm::rewind)else DiscoverConnected(activePerson,onDetail={navigate(Screen.Detail)},onSkip={vm.skip(activePerson.id)},onLike={vm.like(activePerson.id)},onSuper={vm.like(activePerson.id,true)},onRewind=if(ui.subscription.premium)vm::rewind else null)}}
            composable(Screen.Detail.route){if(activePerson==null)DiscoverEmpty(ui.homeLoaded,ui.subscription.premium,onRefresh={navigate(Screen.Discover)},onSettings={navigate(Screen.Settings)},onRewind=vm::rewind)else ProfileDetailConnected(activePerson,onBack={nav.popBackStack()},onLike={vm.like(activePerson.id)},onSuper={vm.like(activePerson.id,true)},onBlock={vm.block(activePerson.id);navigate(Screen.Discover)},onReport={vm.report(activePerson.id,"other","Reported from profile")})}
            composable(Screen.Match.route){MatchConnected(ui.me,ui.matchedProfile,onChat={val conversationId=ui.pendingMatch?.conversationId;vm.consumeMatch();nav.popBackStack();openChat(conversationId)},onContinue={vm.consumeMatch();if(!nav.popBackStack(Screen.Discover.route,false))navigate(Screen.Discover,true)})}
            composable(Screen.Matches.route){MainShell(Screen.Matches,go){MatchesConnected(ui.matches,ui.conversations,onChat={openChat(it)})}}
            composable(Screen.Chats.route){MainShell(Screen.Chats,go){ChatsConnected(ui.conversations,onChat={openChat(it)})}}
            composable(Screen.Chat.route){ChatConnected(ui.messages,ui.me?.id,activeConversation,ui.typing,ui.peerOnline,onBack={nav.popBackStack()},onSend=vm::sendMessage,onPhoto=vm::sendPhoto,onTyping=vm::setTyping,onUnmatch={activeConversation?.matchId?.let{vm.unmatch(it)};navigate(Screen.Chats)},onBlock={activeConversation?.otherUserId?.let{vm.block(it)};navigate(Screen.Chats)},onReport={activeConversation?.otherUserId?.let{vm.report(it,"other","Reported from conversation")}})}
            composable(Screen.Profile.route){MainShell(Screen.Profile,go){MyProfileConnected(ui.me,ui.profilePhotos,onSettings={navigate(Screen.Settings)},onPremium={navigate(Screen.Premium)},onSave=vm::saveProfile,onPhoto=vm::uploadPhoto,onDeletePhoto=vm::deletePhoto)}}
            composable(Screen.Settings.route){SettingsConnected(ui.preferences,ui.me?.hidden==true,ui.blockedUsers,onBack={nav.popBackStack()},onSave=vm::updatePreferences,onHidden=vm::setHidden,onUnblock=vm::unblock,onLogout=vm::logout,onDelete=vm::deleteAccount)}
            composable(Screen.Premium.route){PremiumConnected(ui.subscription,ui.receivedLikes,onBack={nav.popBackStack()},onActivate=vm::activatePremium,onBoost=vm::boost)}
        }
        if(ui.busy)CircularProgressIndicator(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=8.dp).size(24.dp),color=Pink,strokeWidth=3.dp)
        ui.error?.let{Snackbar(Modifier.align(Alignment.BottomCenter).padding(18.dp),action={TextButton(vm::dismissError){Text("Dismiss")}}){Text(it)}}
    } }
}

@Composable fun AuroraBackground(content:@Composable BoxScope.()->Unit) = Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x332E1656),Ink), center=Offset(800f,100f), radius=1000f)), content=content)
@Composable fun BrandMark(size:Dp=64.dp) = Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Pink,Violet))), contentAlignment=Alignment.Center) { Icon(Icons.Filled.Favorite,null,tint=White,modifier=Modifier.size(size*.48f)) }

@Composable fun Splash() = AuroraBackground { Column(Modifier.align(Alignment.Center), horizontalAlignment=Alignment.CenterHorizontally) { BrandMark(82.dp); Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.app_name).uppercase(),style=MaterialTheme.typography.displayLarge); Text(stringResource(R.string.splash_tagline),color=Muted,style=MaterialTheme.typography.labelLarge,letterSpacing=2.sp) } }

@Composable fun AuthHeader(title:String,subtitle:String) { BrandMark(54.dp); Spacer(Modifier.height(24.dp)); Text(title,style=MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(8.dp)); Text(subtitle,color=Muted) }
@Composable fun GlowButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,icon:ImageVector?=null) = Button(onClick,modifier.height(56.dp),shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.buttonColors(containerColor=Pink)) { if(icon!=null){Icon(icon,null);Spacer(Modifier.width(9.dp))};Text(text,fontWeight=FontWeight.Bold) }
@Composable fun DarkField(value:String,onValue:(String)->Unit,label:String,password:Boolean=false) = OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Pink,unfocusedBorderColor=Color(0xFF3A3342),focusedContainerColor=Glass,unfocusedContainerColor=Glass))

@Composable fun Login(busy:Boolean,onCreate:()->Unit,onForgot:()->Unit,onLogin:(String,String)->Unit) = AuroraBackground { var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top=54.dp)) { AuthHeader("Welcome back","Your next great connection is waiting.");Spacer(Modifier.height(38.dp));DarkField(email,{email=it},"Email");Spacer(Modifier.height(14.dp));DarkField(pass,{pass=it},"Password",true);TextButton(onForgot,Modifier.align(Alignment.End)){Text("Forgot password?",color=Pink)};Spacer(Modifier.height(8.dp));GlowButton(if(busy)"Signing in…" else "Sign in",{if(!busy)onLogin(email,pass)},Modifier.fillMaxWidth());Spacer(Modifier.height(26.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){Text(stringResource(R.string.new_to_app,stringResource(R.string.app_name)),color=Muted);Text("Create account",color=Pink,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{onCreate()})};Spacer(Modifier.height(28.dp));Text("By continuing you confirm you are 18+ and accept our Terms & Privacy Policy.",color=Muted,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium) } }

@Composable fun Register(busy:Boolean,onBack:()->Unit,onContinue:(String,String)->Unit)= AuroraBackground { var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")};var repeat by remember{mutableStateOf("")};var mismatch by remember{mutableStateOf(false)};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top=32.dp)){IconButton(onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};Spacer(Modifier.height(18.dp));AuthHeader("Create your account","A little spark can change everything.");Spacer(Modifier.height(30.dp));DarkField(email,{email=it},"Email");Spacer(Modifier.height(14.dp));DarkField(pass,{pass=it},"Create password",true);Spacer(Modifier.height(14.dp));DarkField(repeat,{repeat=it},"Repeat password",true);if(mismatch)Text("Passwords do not match",color=Pink,modifier=Modifier.padding(top=8.dp));Spacer(Modifier.height(24.dp));GlowButton(if(busy)"Creating account…" else "Continue",{mismatch=pass!=repeat;if(!busy&&!mismatch)onContinue(email,pass)},Modifier.fillMaxWidth());Spacer(Modifier.height(20.dp));Text("Protected by encrypted authentication. We never share your contact details.",color=Muted,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium)} }

val onboardingTitles=listOf("What should we call you?","When were you born?","How do you identify?","Who would you like to meet?","Where are you based?","Add your best moments","Tell your story","Choose your interests","What brings you here?")
val onboardingSubs=listOf("First name is perfect.","You must be 18 or older.","Choose what feels right.","You can change this anytime.","We only show approximate distance.","Profiles with 3+ photos get more connections.","A few honest words go a long way.","Pick at least three.","Set the intention, leave room for magic.")

@Composable fun MainShell(screen:Screen,go:(Screen)->Unit,content:@Composable ()->Unit){Scaffold(containerColor=Color.Transparent,bottomBar={NavigationBar(containerColor=Color(0xF5110D16),tonalElevation=0.dp){listOf(Triple(Screen.Discover,"Discover",Icons.Outlined.Explore),Triple(Screen.Matches,"Matches",Icons.Outlined.FavoriteBorder),Triple(Screen.Chats,"Chats",Icons.Outlined.ChatBubbleOutline),Triple(Screen.Profile,"Profile",Icons.Outlined.PersonOutline)).forEach{(s,l,i)->NavigationBarItem(screen==s,{go(s)},{Icon(i,l)},label={Text(l)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Pink,selectedTextColor=Pink,indicatorColor=Color(0x332E1656),unselectedIconColor=Muted,unselectedTextColor=Muted))}}}){p->Box(Modifier.padding(p)){content()}}}
@Composable fun SurfaceCard(modifier:Modifier=Modifier,padding:Dp=16.dp,content:@Composable ColumnScope.()->Unit)=Column(modifier.clip(RoundedCornerShape(24.dp)).background(Glass).border(1.dp,Color(0x224F405E),RoundedCornerShape(24.dp)).padding(padding),content=content)

@Composable fun Action(icon:ImageVector,color:Color,size:Dp,description:String,onClick:()->Unit)=IconButton(onClick,Modifier.size(size).shadow(18.dp,CircleShape,spotColor=color).background(Color(0xFF201927),CircleShape).border(1.dp,color.copy(.5f),CircleShape)){Icon(icon,description,tint=color,modifier=Modifier.size(size*.42f))}

@Composable fun Top(title:String,onBack:()->Unit,action:(@Composable ()->Unit)?=null)=Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));action?.invoke()}


@Composable
fun MatchConnected(me:MyProfile?,person:DiscoverProfile?,onChat:()->Unit,onContinue:()->Unit)=Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF522146),Ink),radius=1100f))){
    Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("IT'S A",style=MaterialTheme.typography.displayLarge);Text("MATCH",color=Pink,style=MaterialTheme.typography.displayLarge);Text("You and ${person?.name?:"someone special"} felt the same spark.",color=Muted,textAlign=TextAlign.Center);Spacer(Modifier.height(42.dp));Box(Modifier.height(230.dp).fillMaxWidth()){MatchPortrait(me?.photoUrl,"Your photo",Violet,Modifier.align(Alignment.CenterStart).offset(x=32.dp).rotate(-8f));MatchPortrait(person?.photoUrl,person?.name?:"Match",Pink,Modifier.align(Alignment.CenterEnd).offset(x=(-32).dp).rotate(8f));BrandMark(58.dp)};Spacer(Modifier.height(44.dp));GlowButton("Send a message",onChat,Modifier.fillMaxWidth(),Icons.AutoMirrored.Filled.Chat);TextButton(onContinue){Text("Keep discovering",color=White)}}
}

@Composable private fun MatchPortrait(url:String?,description:String,border:Color,modifier:Modifier){val shape=RoundedCornerShape(30.dp);val styled=modifier.size(190.dp).clip(shape).border(3.dp,border,shape);if(url!=null)AsyncImage(url,description,styled,contentScale=ContentScale.Crop)else Box(styled.background(Brush.linearGradient(listOf(Color(0xFF392743),Color(0xFF17131E)))),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Person,description,tint=Muted,modifier=Modifier.size(72.dp))}}

@Composable fun SectionTitle(title:String,action:String?=null)=Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));if(action!=null)Text(action,color=Pink)}
@Composable fun ConversationRow(p:Person,msg:String,time:String,unread:Int,onClick:()->Unit)=SurfaceCard(Modifier.fillMaxWidth().clickable{onClick()},padding=14.dp){Row(verticalAlignment=Alignment.CenterVertically){Box{PersonPhoto(p,Modifier.size(62.dp).clip(CircleShape));Box(Modifier.align(Alignment.BottomEnd).size(14.dp).background(Color(0xFF55E6A5),CircleShape).border(3.dp,Surface,CircleShape))};Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text(msg,color=Muted,maxLines=1)};Column(horizontalAlignment=Alignment.End){Text(time,color=Muted,style=MaterialTheme.typography.bodyMedium);if(unread>0){Spacer(Modifier.height(7.dp));Box(Modifier.size(22.dp).background(Pink,CircleShape),contentAlignment=Alignment.Center){Text("$unread",style=MaterialTheme.typography.labelLarge)}}}}}



@Composable fun SettingsHeader(t:String){Spacer(Modifier.height(24.dp));Text(t,color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(9.dp))}


@Composable
fun PremiumConnected(subscription:Subscription,likes:ReceivedLikes,onBack:()->Unit,onActivate:()->Unit,onBoost:()->Unit)=Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF261530),Ink)))){
    LazyColumn(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Row(Modifier.fillMaxWidth()){IconButton(onBack){Icon(Icons.Default.Close,"Close")}};BrandMark(68.dp);Spacer(Modifier.height(12.dp));Text(if(subscription.premium)stringResource(R.string.premium_name,stringResource(R.string.app_name).uppercase()) else stringResource(R.string.app_name).uppercase(),color=Pink,style=MaterialTheme.typography.headlineLarge);Text(if(subscription.premium)"Your premium access is active" else "More choice. More control. More meaningful sparks.",color=Muted,textAlign=TextAlign.Center)}
        item{SurfaceCard(Modifier.fillMaxWidth()){Text("TODAY",color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(8.dp));Text("${subscription.actionsUsed} of ${subscription.actionLimit} discovery actions");Spacer(Modifier.height(8.dp));LinearProgressIndicator(progress={if(subscription.actionLimit==0)0f else subscription.actionsUsed.toFloat()/subscription.actionLimit},Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),color=Pink,trackColor=Color(0xFF30263A));Spacer(Modifier.height(12.dp));Text("${subscription.superLikesUsed} of ${subscription.superLikeLimit} Super Likes used",color=Muted)} }
        item{SurfaceCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Favorite,null,tint=Pink);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("People who like you",fontWeight=FontWeight.Bold);Text(if(likes.locked)"${likes.count} waiting · Premium unlocks the list" else "${likes.count} people already interested",color=Muted)}};if(!likes.locked&&likes.people.isNotEmpty()){Spacer(Modifier.height(14.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(likes.people,key={it.id}){person->Column(horizontalAlignment=Alignment.CenterHorizontally){PersonPhoto(avatarPerson(person.id,person.name,person.photoUrl),Modifier.size(62.dp).clip(CircleShape));Text(person.name,style=MaterialTheme.typography.labelMedium)}}}}}}
        items(listOf(Triple(Icons.Default.Replay,"Rewind","Give the last profile another chance"),Triple(Icons.Default.Bolt,"Boost","Move your profile higher for 30 minutes"),Triple(Icons.Default.Star,"More Super Likes","Stand out to people who matter"))){(icon,title,subtitle)->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).background(Color(0x332E1656),RoundedCornerShape(15.dp)),contentAlignment=Alignment.Center){Icon(icon,null,tint=Pink)};Spacer(Modifier.width(12.dp));Column{Text(title,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted)}}}
        if(subscription.premium)item{GlowButton("Activate a 30-minute boost",onBoost,Modifier.fillMaxWidth(),Icons.Default.Bolt);subscription.validUntil?.let{Text("Premium through ${it.take(10)}",color=Muted)}}else item{SurfaceCard(Modifier.fillMaxWidth()){Text("DEVELOPMENT PLAN",color=Pink,fontWeight=FontWeight.Bold);Text("Payment boundary is ready; this build activates Premium without charging.",color=Muted);Spacer(Modifier.height(14.dp));GlowButton("Activate Premium",onActivate,Modifier.fillMaxWidth())}}
        item{Spacer(Modifier.height(20.dp))}
    }
}

@Composable
fun PersonPhoto(person:Person,modifier:Modifier){
    if(person.photoUrl!=null) AsyncImage(model=person.photoUrl,contentDescription="${person.name} profile photo",modifier=modifier,contentScale=ContentScale.Crop)
    else Image(painterResource(person.image),"${person.name} profile photo",modifier,contentScale=ContentScale.Crop)
}
private fun avatarPerson(id:String,name:String,photoUrl:String?)=Person(id,name,18,"","","",R.drawable.maya,photoUrl=photoUrl)

@Composable
fun DiscoverConnected(person:Person,onDetail:()->Unit,onSkip:()->Unit,onLike:()->Unit,onSuper:()->Unit,onRewind:(()->Unit)?=null){
    var offset by remember(person.id){mutableFloatStateOf(0f)}
    AuroraBackground {
        Column(Modifier.fillMaxSize().padding(horizontal=18.dp)){
            Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically){BrandMark(38.dp);Spacer(Modifier.width(12.dp));Text("Discover",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.weight(1f));if(onRewind!=null)IconButton(onRewind){Icon(Icons.Default.Replay,"Rewind last pass")}}
            Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer{rotationZ=offset/42;translationX=offset}.pointerInput(person.id){detectDragGestures(onDragEnd={when{offset>180->onLike();offset<(-180)->onSkip()};offset=0f}){change,drag->change.consume();offset+=drag.x}}.clip(RoundedCornerShape(30.dp)).clickable(onClick=onDetail)){
                PersonPhoto(person,Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Transparent,Color(0xEE09070D)))))
                Row(Modifier.align(Alignment.TopStart).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(if(person.online)Color(0xFF55E6A5)else Muted,CircleShape));Spacer(Modifier.width(7.dp));Text(if(person.online)"ONLINE" else "RECENTLY ACTIVE",style=MaterialTheme.typography.labelLarge)}
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp)){Row(verticalAlignment=Alignment.Bottom){Text("${person.name}, ${person.age}",style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.width(8.dp));Icon(Icons.Default.Verified,"Verified profile",tint=Violet,modifier=Modifier.size(22.dp))};Text("${person.city}  •  ${person.distance} away",color=Color.White.copy(.8f));if(person.interests.isNotEmpty()){Spacer(Modifier.height(12.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){person.interests.take(3).forEach{AssistChip({}, {Text(it)},colors=AssistChipDefaults.assistChipColors(containerColor=Color(0x99211829)),border=null)}}}}
            }
            Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){Action(Icons.Default.Close,Color(0xFF9B93A5),54.dp,"Pass",onSkip);Action(Icons.Default.Star,Violet,62.dp,"Super Like",onSuper);Action(Icons.Default.Favorite,Pink,72.dp,"Like",onLike)}
        }
    }
}

@Composable
fun DiscoverEmpty(loaded:Boolean,premium:Boolean,onRefresh:()->Unit,onSettings:()->Unit,onRewind:()->Unit)=AuroraBackground{
    Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        if(!loaded){CircularProgressIndicator(color=Pink);Spacer(Modifier.height(18.dp));Text("Finding people nearby…",color=Muted)}else{BrandMark(72.dp);Spacer(Modifier.height(22.dp));Text("You’re all caught up",style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center);Spacer(Modifier.height(10.dp));Text("Try widening your distance or age range, or check back when new people join.",color=Muted,textAlign=TextAlign.Center);Spacer(Modifier.height(26.dp));GlowButton("Refresh",onRefresh,Modifier.fillMaxWidth());Spacer(Modifier.height(10.dp));OutlinedButton(onSettings,Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Outlined.Tune,null);Spacer(Modifier.width(8.dp));Text("Adjust discovery filters")};if(premium){Spacer(Modifier.height(10.dp));TextButton(onRewind){Icon(Icons.Default.Replay,null);Spacer(Modifier.width(8.dp));Text("Rewind last pass")}}}
    }
}

@Composable
fun ProfileDetailConnected(person:Person,onBack:()->Unit,onLike:()->Unit,onSuper:()->Unit,onBlock:()->Unit,onReport:()->Unit){
    var menu by remember{mutableStateOf(false)}
    var confirmBlock by remember{mutableStateOf(false)}
    AuroraBackground { Box {
        LazyColumn(Modifier.fillMaxSize()){
            item{Box(Modifier.fillMaxWidth().height(500.dp)){val urls=person.photoUrls.ifEmpty{listOfNotNull(person.photoUrl)};if(urls.isEmpty())PersonPhoto(person,Modifier.fillMaxSize())else LazyRow(Modifier.fillMaxSize()){items(urls){url->AsyncImage(url,"${person.name} profile photo",Modifier.fillParentMaxWidth().fillMaxHeight(),contentScale=ContentScale.Crop)}};Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Ink))));Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)){IconButton(onBack,Modifier.background(Color(0x77211B2B),CircleShape)){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};Spacer(Modifier.weight(1f));Box{IconButton({menu=true},Modifier.background(Color(0x77211B2B),CircleShape)){Icon(Icons.Default.MoreVert,"Safety actions")};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Report profile")},{menu=false;onReport()},leadingIcon={Icon(Icons.Outlined.Flag,null)});DropdownMenuItem({Text("Block profile")},{menu=false;confirmBlock=true},leadingIcon={Icon(Icons.Outlined.Block,null)})}}};Column(Modifier.align(Alignment.BottomStart).padding(24.dp)){Text("${person.name}, ${person.age}",style=MaterialTheme.typography.headlineLarge);Text("${person.city}  •  ${person.distance} away",color=Muted);if(urls.size>1)Text("Swipe for ${urls.size} photos",color=Muted,style=MaterialTheme.typography.labelMedium)}}}
            item{Column(Modifier.padding(horizontal=24.dp)){Text("About",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(10.dp));Text(person.bio,color=Color.White.copy(.84f));if(person.interests.isNotEmpty()){Spacer(Modifier.height(24.dp));Text("Interests",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(10.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){person.interests.forEach{SurfaceCard(padding=10.dp){Text(it)}}}};Spacer(Modifier.height(24.dp));SurfaceCard(Modifier.fillMaxWidth()){Text("LOOKING FOR",color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(6.dp));Text(person.goal.replace('_',' ').replaceFirstChar{it.uppercase()},fontWeight=FontWeight.SemiBold)};Spacer(Modifier.height(120.dp))}}
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Ink))).padding(24.dp)){Action(Icons.Default.Star,Violet,58.dp,"Super Like",onSuper);Spacer(Modifier.width(20.dp));GlowButton("Like ${person.name}",onLike,Modifier.weight(1f),Icons.Default.Favorite)}
        if(confirmBlock)AlertDialog(onDismissRequest={confirmBlock=false},title={Text("Block ${person.name}?")},text={Text("You will no longer see each other. ${person.name} will not be notified.")},confirmButton={TextButton({confirmBlock=false;onBlock()}){Text("Block",color=Pink)}},dismissButton={TextButton({confirmBlock=false}){Text("Cancel")}})
    } }
}

@Composable
fun MatchesConnected(matches:List<MatchSummary>,conversations:List<Conversation>,onChat:(String)->Unit)=AuroraBackground{
    LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Spacer(Modifier.height(6.dp));Text("Matches",style=MaterialTheme.typography.headlineLarge);Text("Connections with potential",color=Muted)}
        item{SectionTitle("New sparks",if(matches.isNotEmpty())"${matches.size}" else null)}
        if(matches.isEmpty())item{SurfaceCard(Modifier.fillMaxWidth()){Text("No matches yet",fontWeight=FontWeight.Bold);Text("A mutual like will appear here.",color=Muted)}}else item{LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)){items(matches,key={it.id}){match->Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.clickable{conversations.firstOrNull{it.matchId==match.id}?.id?.let(onChat)}){PersonPhoto(avatarPerson(match.id,match.name,match.photoUrl),Modifier.size(78.dp).clip(CircleShape).border(2.dp,Brush.linearGradient(listOf(Pink,Violet)),CircleShape));Spacer(Modifier.height(7.dp));Text(match.name)}}}}
        item{SectionTitle("Your conversations")}
        if(conversations.isEmpty())item{SurfaceCard(Modifier.fillMaxWidth()){Text("No conversations yet",fontWeight=FontWeight.Bold);Text("When you match, start with something from their profile.",color=Muted)}}
        else items(conversations,key={it.id}){c->ConversationRow(avatarPerson(c.otherUserId?:c.id,c.name,c.photoUrl),c.lastMessage?:"You matched — say hello",c.lastMessageAt?.take(10)?:"New",c.unreadCount){onChat(c.id)}}
    }
}

@Composable
fun ChatsConnected(conversations:List<Conversation>,onChat:(String)->Unit)=AuroraBackground{
    LazyColumn(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
        item{Spacer(Modifier.height(6.dp));Text("Chats",style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(12.dp));DarkField("",{},"Search conversations")}
        if(conversations.isEmpty())item{SurfaceCard(Modifier.fillMaxWidth()){Text("Your conversations will appear here",fontWeight=FontWeight.Bold);Text("Like someone who likes you back to start chatting.",color=Muted)}}
        else items(conversations,key={it.id}){c->ConversationRow(avatarPerson(c.otherUserId?:c.id,c.name,c.photoUrl),c.lastMessage?:"You matched — say hello",c.lastMessageAt?.take(10)?:"New",c.unreadCount){onChat(c.id)}}
    }
}

@Composable
fun ChatConnected(
    messages:List<ChatMessage>,
    myUserId:String?,
    conversation:Conversation?,
    typing:Boolean,
    peerOnline:Boolean,
    onBack:()->Unit,
    onSend:(String,String?)->Unit,
    onPhoto:(ByteArray,String,String?)->Unit,
    onTyping:(Boolean)->Unit,
    onUnmatch:()->Unit,
    onBlock:()->Unit,
    onReport:()->Unit,
){
    var text by remember{mutableStateOf("")};var reply by remember{mutableStateOf<ChatMessage?>(null)};var menu by remember{mutableStateOf(false)};var confirmation by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current;val scope=rememberCoroutineScope();val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){val mime=context.contentResolver.getType(uri)?:"image/jpeg";val replyId=reply?.id;scope.launch(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{onPhoto(it.readBytes(),mime,replyId)};reply=null}}}
    AuroraBackground { Column(Modifier.fillMaxSize()){
        Top(conversation?.name?:"Chat",onBack){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(8.dp).background(if(peerOnline)Color(0xFF55E6A5)else Muted,CircleShape));Spacer(Modifier.width(6.dp));Text(if(typing)"typing…" else if(peerOnline)"online" else "recently active",color=Muted);Box{IconButton({menu=true}){Icon(Icons.Default.MoreVert,"Conversation actions")};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text("Report")},{menu=false;onReport()},leadingIcon={Icon(Icons.Outlined.Flag,null)});DropdownMenuItem({Text("Unmatch")},{menu=false;confirmation="unmatch"},leadingIcon={Icon(Icons.Outlined.HeartBroken,null)});DropdownMenuItem({Text("Block")},{menu=false;confirmation="block"},leadingIcon={Icon(Icons.Outlined.Block,null)})}}}}
        LazyColumn(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp),reverseLayout=true){
            if(messages.isEmpty())item{Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Outlined.WavingHand,null,tint=Violet,modifier=Modifier.size(38.dp));Spacer(Modifier.height(12.dp));Text("Start with something from their profile",color=Muted,textAlign=TextAlign.Center)}}
            else items(messages.asReversed(),key={it.id}){message->MessageBubble(message,message.senderId==myUserId,messages.firstOrNull{it.id==message.replyToId},onReply={reply=message})}
        }
        reply?.let{Row(Modifier.fillMaxWidth().background(Glass).padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.AutoMirrored.Outlined.Reply,null,tint=Violet);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text("Replying",color=Violet,style=MaterialTheme.typography.labelLarge);Text(it.body?:"Photo",maxLines=1)};IconButton({reply=null}){Icon(Icons.Default.Close,"Cancel reply")}}}
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){IconButton({picker.launch("image/*")}){Icon(Icons.Outlined.AddPhotoAlternate,"Attach photo",tint=Muted)};IconButton({text+="😊";onTyping(true)}){Icon(Icons.Outlined.EmojiEmotions,"Add emoji",tint=Muted)};OutlinedTextField(text,{text=it;onTyping(it.isNotBlank())},Modifier.weight(1f),placeholder={Text("Message…")},shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=Glass,focusedContainerColor=Glass,unfocusedBorderColor=Color.Transparent,focusedBorderColor=Pink));IconButton({val value=text;if(value.isNotBlank()){text="";onTyping(false);onSend(value,reply?.id);reply=null}},Modifier.padding(start=8.dp).background(Pink,CircleShape)){Icon(Icons.Default.ArrowUpward,"Send")}}
    };confirmation?.let{action->AlertDialog(onDismissRequest={confirmation=null},title={Text(if(action=="block")"Block this person?" else "End this match?")},text={Text(if(action=="block")"You will no longer see or message each other." else "This conversation will be removed for both people.")},confirmButton={TextButton({confirmation=null;if(action=="block")onBlock()else onUnmatch()}){Text(if(action=="block")"Block" else "Unmatch",color=Pink)}},dismissButton={TextButton({confirmation=null}){Text("Cancel")}})} }
}

@Composable
fun MessageBubble(message:ChatMessage,mine:Boolean,replied:ChatMessage?,onReply:()->Unit)=Row(Modifier.fillMaxWidth(),horizontalArrangement=if(mine)Arrangement.End else Arrangement.Start){
    Column(horizontalAlignment=if(mine)Alignment.End else Alignment.Start){SurfaceCard(Modifier.widthIn(max=290.dp),padding=13.dp){if(replied!=null){Text(replied.body?:"Photo",color=Violet,style=MaterialTheme.typography.bodySmall,maxLines=2);Spacer(Modifier.height(7.dp))};if(message.mediaUrl!=null)AsyncImage(message.mediaUrl,"Shared photo",Modifier.fillMaxWidth().heightIn(max=280.dp).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Crop);message.body?.let{if(message.mediaUrl!=null)Spacer(Modifier.height(8.dp));Text(it)}};Row(verticalAlignment=Alignment.CenterVertically){TextButton(onReply,contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)){Icon(Icons.AutoMirrored.Outlined.Reply,null,modifier=Modifier.size(14.dp));Spacer(Modifier.width(3.dp));Text("Reply",style=MaterialTheme.typography.labelSmall)};if(mine&&message.readAt!=null)Text("Read",color=Muted,style=MaterialTheme.typography.labelSmall)}}
}

@Composable
fun SettingsConnected(initial:SearchPreferences,initialHidden:Boolean,blocked:List<BlockedUser>,onBack:()->Unit,onSave:(SearchPreferences)->Unit,onHidden:(Boolean)->Unit,onUnblock:(String)->Unit,onLogout:()->Unit,onDelete:()->Unit){
    var minAge by remember(initial){mutableFloatStateOf(initial.minAge.toFloat())};var maxAge by remember(initial){mutableFloatStateOf(initial.maxAge.toFloat())};var distance by remember(initial){mutableFloatStateOf(initial.maxDistanceKm.toFloat())};var notifications by remember(initial){mutableStateOf(initial.notifications)};var showGenders by remember(initial){mutableStateOf(initial.showGenders.toSet())};var hidden by remember(initialHidden){mutableStateOf(initialHidden)};var deleteDialog by remember{mutableStateOf(false)}
    AuroraBackground { LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Top("Settings",onBack)}
        item{SettingsHeader("DISCOVERY")}
        item{SurfaceCard(Modifier.fillMaxWidth()){Text("Age range: ${minAge.toInt()} – ${maxAge.toInt()}",fontWeight=FontWeight.SemiBold);RangeSlider(value=minAge..maxAge,onValueChange={minAge=it.start;maxAge=it.endInclusive},valueRange=18f..80f)}}
        item{SurfaceCard(Modifier.fillMaxWidth()){Text("Maximum distance: ${distance.toInt()} km",fontWeight=FontWeight.SemiBold);Slider(distance,{distance=it},valueRange=5f..200f)}}
        item{SurfaceCard(Modifier.fillMaxWidth()){Text("Show me",fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(10.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("woman" to "Women","man" to "Men","non_binary" to "Non-binary","other" to "Other identities").forEach{(value,label)->FilterChip(selected=value in showGenders,onClick={showGenders=if(value in showGenders&&showGenders.size>1)showGenders-value else showGenders+value},label={Text(label)})}}}}
        item{GlowButton("Save discovery settings",{onSave(SearchPreferences(minAge.toInt(),maxAge.toInt(),distance.toInt(),showGenders.toList(),notifications))},Modifier.fillMaxWidth())}
        item{SettingsHeader("PRIVACY & SAFETY")}
        item{SurfaceCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Text("Notifications",Modifier.weight(1f));Switch(notifications,{notifications=it})}}}
        item{SurfaceCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Hide my profile");Text("Existing matches can still message you",color=Muted,style=MaterialTheme.typography.bodyMedium)};Switch(hidden,{hidden=it;onHidden(it)})}}}
        if(blocked.isNotEmpty()){item{Text("Blocked profiles",style=MaterialTheme.typography.titleMedium)};items(blocked,key={it.id}){person->SurfaceCard(Modifier.fillMaxWidth()){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(person.name?:"Blocked member",fontWeight=FontWeight.SemiBold);Text("Cannot see or contact you",color=Muted,style=MaterialTheme.typography.bodySmall)};TextButton({onUnblock(person.id)}){Text("Unblock",color=Pink)}}}}}
        item{SettingsHeader("ACCOUNT")}
        item{TextButton(onLogout,Modifier.fillMaxWidth()){Text("Sign out",color=Pink)}}
        item{TextButton({deleteDialog=true},Modifier.fillMaxWidth()){Text("Delete account",color=Muted)};Spacer(Modifier.height(24.dp))}
    };if(deleteDialog)AlertDialog(onDismissRequest={deleteDialog=false},title={Text("Delete your account?")},text={Text("This permanently removes your profile, photos, matches and messages. This cannot be undone.")},confirmButton={TextButton({deleteDialog=false;onDelete()}){Text("Delete permanently",color=Pink)}},dismissButton={TextButton({deleteDialog=false}){Text("Cancel")}}) }
}

@Composable
fun Recovery(developmentToken:String?,onBack:()->Unit,onRecover:(String)->Unit,onReset:(String,String)->Unit){
    var email by remember{mutableStateOf("")};var requested by remember{mutableStateOf(false)};var token by remember(developmentToken){mutableStateOf(developmentToken.orEmpty())};var password by remember{mutableStateOf("")}
    AuroraBackground { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp).padding(top=32.dp)){
        IconButton(onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};Spacer(Modifier.height(18.dp));AuthHeader("Restore access","We will help you get back to your connections.");Spacer(Modifier.height(30.dp));DarkField(email,{email=it},"Account email");Spacer(Modifier.height(18.dp));GlowButton("Send reset instructions",{requested=true;onRecover(email)},Modifier.fillMaxWidth())
        if(requested){Spacer(Modifier.height(30.dp));Text("Enter the token from your email",style=MaterialTheme.typography.titleLarge);if(developmentToken!=null){Spacer(Modifier.height(8.dp));Text("Development mode: token filled automatically.",color=Violet)};Spacer(Modifier.height(16.dp));DarkField(token,{token=it},"Reset token");Spacer(Modifier.height(12.dp));DarkField(password,{password=it},"New password",true);Spacer(Modifier.height(18.dp));GlowButton("Update password",{onReset(token,password)},Modifier.fillMaxWidth())}
    } }
}

@Composable
fun OnboardingConnected(onDone:(ProfileDraft,List<String>,List<String>)->Unit,onPhoto:(Int,ByteArray,String)->Unit,initialUploadedPositions:Set<Int> = emptySet()){
    var step by remember{mutableIntStateOf(0)};var name by remember{mutableStateOf("")};var birth by remember{mutableStateOf("1997-04-12")};var gender by remember{mutableStateOf("man")};var lookingFor by remember{mutableStateOf("woman")};var city by remember{mutableStateOf("Lisbon")};var bio by remember{mutableStateOf("")};var goal by remember{mutableStateOf("relationship")};var selectedInterests by remember{mutableStateOf(setOf<String>())};var uploadedPositions by remember{mutableStateOf(initialUploadedPositions)};var pendingPosition by remember{mutableIntStateOf(0)}
    val context=LocalContext.current;val scope=rememberCoroutineScope();val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){val mime=context.contentResolver.getType(uri)?:"image/jpeg";val position=pendingPosition;uploadedPositions=uploadedPositions+position;scope.launch(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{onPhoto(position,it.readBytes(),mime)}}}}
    val options=when(step){2->listOf("Woman" to "woman","Man" to "man","Non-binary" to "non_binary","Self-describe" to "other");3->listOf("Women" to "woman","Men" to "man","Everyone" to "everyone");7->listOf("Travel" to "Travel","Coffee" to "Coffee","Design" to "Design","Live music" to "Live music","Hiking" to "Hiking","Cooking" to "Cooking","Photography" to "Photography","Books" to "Books");8->listOf("A relationship" to "relationship","Good conversations" to "communication","Dating" to "dating","Friendship" to "friendship","Still figuring it out" to "unknown");else->emptyList()}
    AuroraBackground { Column(Modifier.fillMaxSize().padding(24.dp).padding(top=32.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){if(step>0)IconButton({step--}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Previous step")}else Spacer(Modifier.width(48.dp));LinearProgressIndicator(progress={(step+1)/9f},Modifier.weight(1f).height(6.dp).clip(CircleShape),color=Pink,trackColor=Color(0xFF2B2333));Spacer(Modifier.width(48.dp))};Spacer(Modifier.height(48.dp));Text(onboardingTitles[step],style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(8.dp));Text(onboardingSubs[step],color=Muted);Spacer(Modifier.height(28.dp))
        when(step){
            0->DarkField(name,{name=it},"Your name")
            1->DarkField(birth,{birth=it},"Date of birth · YYYY-MM-DD")
            4->DarkField(city,{city=it},"City")
            5->Column{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){repeat(3){position->SurfaceCard(Modifier.weight(1f).aspectRatio(.72f).clickable{pendingPosition=position;picker.launch("image/*")},padding=0.dp){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(if(position in uploadedPositions)Icons.Default.CheckCircle else Icons.Default.AddPhotoAlternate,null,tint=if(position in uploadedPositions)Color(0xFF55E6A5)else Pink,modifier=Modifier.size(38.dp));Spacer(Modifier.height(8.dp));Text(if(position in uploadedPositions)"Added" else "Photo ${position+1}",style=MaterialTheme.typography.labelMedium)}}}}};Spacer(Modifier.height(12.dp));Text("Add at least one. You can add up to 9 later.",color=Muted)}
            6->OutlinedTextField(bio,{bio=it},Modifier.fillMaxWidth().height(160.dp),label={Text("About you")},shape=RoundedCornerShape(18.dp),colors=OutlinedTextFieldDefaults.colors(focusedContainerColor=Glass,unfocusedContainerColor=Glass,focusedBorderColor=Pink,unfocusedBorderColor=Color(0xFF3A3342)))
            else->LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp)){items(options){(label,value)->val selected=when(step){2->gender==value;3->lookingFor==value;7->value in selectedInterests;8->goal==value;else->false};SurfaceCard(Modifier.fillMaxWidth().clickable{when(step){2->gender=value;3->lookingFor=value;7->selectedInterests=if(selected)selectedInterests-value else selectedInterests+value;8->goal=value}},padding=18.dp){Row(verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);Icon(if(selected)Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,null,tint=if(selected)Pink else Muted)}}}}
        }
        Spacer(Modifier.weight(1f));val valid=when(step){0->name.isNotBlank();1->Regex("\\d{4}-\\d{2}-\\d{2}").matches(birth);4->city.isNotBlank();5->uploadedPositions.isNotEmpty();7->selectedInterests.size>=3;else->true};GlowButton(if(step==8)stringResource(R.string.enter_app,stringResource(R.string.app_name)) else "Continue",{if(valid){if(step<8)step++ else{val genders=if(lookingFor=="everyone")listOf("woman","man","non_binary","other")else listOf(lookingFor);onDone(ProfileDraft(name,birth,gender,city,bio.ifBlank{"Curious mind, slow coffee, spontaneous weekends."},goal),selectedInterests.toList(),genders)}}},Modifier.fillMaxWidth());if(!valid){Spacer(Modifier.height(8.dp));Text(when(step){5->"Add at least one profile photo";7->"Choose at least three interests";else->"Complete this field to continue"},color=Pink,modifier=Modifier.align(Alignment.CenterHorizontally))};Spacer(Modifier.height(18.dp))
    } }
}

@Composable
fun MyProfileConnected(
    me:MyProfile?,
    photos:List<ProfilePhoto>,
    onSettings:()->Unit,
    onPremium:()->Unit,
    onSave:(ProfileDraft,List<String>)->Unit,
    onPhoto:(Int,ByteArray,String)->Unit,
    onDeletePhoto:(String)->Unit,
){
    var edit by remember{mutableStateOf(false)};var pendingPosition by remember{mutableIntStateOf(0)}
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null){val mime=context.contentResolver.getType(uri)?:"image/jpeg";val position=pendingPosition;scope.launch(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{onPhoto(position,it.readBytes(),mime)}}}}
    AuroraBackground { LazyColumn(Modifier.fillMaxSize().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("My profile",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f));IconButton(onSettings){Icon(Icons.Outlined.Settings,"Settings")}}}
        item{val primary=photos.firstOrNull{it.position==0}?.url?:me?.photoUrl;Box(Modifier.clickable{pendingPosition=0;picker.launch("image/*")}){val avatar=Modifier.size(144.dp).clip(CircleShape).border(3.dp,Brush.linearGradient(listOf(Pink,Violet)),CircleShape);if(primary!=null)AsyncImage(primary,"Your profile photo",avatar,contentScale=ContentScale.Crop)else Box(avatar.background(Brush.linearGradient(listOf(Color(0xFF32233E),Color(0xFF17131E)))),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Person,"Add your profile photo",tint=Muted,modifier=Modifier.size(64.dp))};Box(Modifier.align(Alignment.BottomEnd).size(38.dp).background(Pink,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Edit,null,modifier=Modifier.size(20.dp))}};Text(me?.name?:"Your profile",style=MaterialTheme.typography.headlineMedium);Text(me?.city?:"Add your city",color=Muted)}
        item{Column(Modifier.fillMaxWidth()){Text("PHOTOS",color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(8.dp));LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(6){position->val photo=photos.firstOrNull{it.position==position};Box(Modifier.size(104.dp,138.dp).clip(RoundedCornerShape(18.dp)).background(Glass).border(1.dp,Color(0x334F405E),RoundedCornerShape(18.dp)).clickable{pendingPosition=position;picker.launch("image/*")},contentAlignment=Alignment.Center){if(photo!=null){AsyncImage(photo.url,"Profile photo ${position+1}",Modifier.fillMaxSize(),contentScale=ContentScale.Crop);IconButton({onDeletePhoto(photo.id)},Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).background(Color(0xAA0A0710),CircleShape)){Icon(Icons.Default.Close,"Delete photo",modifier=Modifier.size(16.dp))}}else Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.AddPhotoAlternate,null,tint=Muted);Text("Add",color=Muted,style=MaterialTheme.typography.labelSmall)}}}}}}
        item{SurfaceCard(Modifier.fillMaxWidth().clickable{onPremium()}){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AutoAwesome,null,tint=Pink);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(stringResource(R.string.premium_name,stringResource(R.string.app_name)),fontWeight=FontWeight.Bold);Text("See likes, rewind and boost",color=Muted)};Icon(Icons.Default.ChevronRight,null)}}}
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Your story",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.weight(1f));TextButton({edit=true}){Text("Edit")}}}
        item{SurfaceCard(Modifier.fillMaxWidth()){Text("ABOUT",color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(8.dp));Text(me?.bio.orEmpty().ifBlank{"Add a few words about yourself."});if(!me?.interests.isNullOrEmpty()){Spacer(Modifier.height(20.dp));Text("INTERESTS",color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(8.dp));Text(me!!.interests.joinToString("  •  "),color=Muted)};Spacer(Modifier.height(20.dp));Text("LOOKING FOR",color=Pink,style=MaterialTheme.typography.labelLarge);Spacer(Modifier.height(8.dp));Text((me?.datingGoal?:"relationship").replace('_',' ').replaceFirstChar{it.uppercase()},color=Muted)}}
        item{Spacer(Modifier.height(18.dp))}
    };if(edit){var name by remember{mutableStateOf(me?.name.orEmpty())};var city by remember{mutableStateOf(me?.city.orEmpty())};var bio by remember{mutableStateOf(me?.bio.orEmpty())};var goal by remember{mutableStateOf(me?.datingGoal?:"relationship")};var selected by remember{mutableStateOf(me?.interests.orEmpty().toSet())};val options=listOf("Travel","Coffee","Design","Live music","Hiking","Cooking","Photography","Books");AlertDialog(onDismissRequest={edit=false},title={Text("Edit profile")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){DarkField(name,{name=it},"Name");DarkField(city,{city=it},"City");OutlinedTextField(bio,{bio=it},Modifier.fillMaxWidth(),label={Text("Bio")});Text("Dating goal",color=Muted);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Relationship" to "relationship","Conversations" to "communication","Dating" to "dating","Friendship" to "friendship","Not sure" to "unknown").forEach{(label,value)->FilterChip(goal==value,{goal=value},{Text(label)})}};Text("Interests · choose at least 3",color=Muted);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){options.forEach{value->FilterChip(value in selected,{selected=if(value in selected)selected-value else selected+value},{Text(value)})}}}},confirmButton={TextButton({if(selected.size>=3&&name.isNotBlank()&&city.isNotBlank()){edit=false;onSave(ProfileDraft(name,me?.birthDate?.take(10)?:"1997-04-12",me?.gender?:"man",city,bio,goal),selected.toList())}}){Text("Save",color=if(selected.size>=3&&name.isNotBlank()&&city.isNotBlank())Pink else Muted)}},dismissButton={TextButton({edit=false}){Text("Cancel")}})} }
}
